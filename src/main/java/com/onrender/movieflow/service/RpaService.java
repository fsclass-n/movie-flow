package com.onrender.movieflow.service;

import com.onrender.movieflow.dto.MovieDto;
import com.onrender.movieflow.event.MovieUpdatedEvent;
import com.onrender.movieflow.repository.MovieRepository;
import com.onrender.movieflow.util.SeatUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RpaService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final MovieRepository movieRepository;
    private final AtomicInteger activeJobCount = new AtomicInteger(0);
    private final AtomicBoolean initialCrawlRequested = new AtomicBoolean(false);

    public RpaService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                      ApplicationEventPublisher eventPublisher, MovieRepository movieRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.movieRepository = movieRepository;
    }

    /**
     * 서버 기동 완료(포트 8080 오픈) 직후에 실행됩니다.
     * Render의 Port Scan Timeout 문제를 방지하는 핵심 로직입니다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("✅ 서버 포트 감지 성공. 최초 RPA 크롤링을 시작합니다.");
        runInitialCrawlIfNeeded();
    }

    public int getActiveJobCount() {
        return activeJobCount.get();
    }

    public boolean runRpaScript() {
        if (activeJobCount.get() > 0) {
            log.warn("⚠️ 이미 실행 중인 RPA 작업이 있습니다.");
            return false;
        }
        executeAsyncRpa();
        return true;
    }

    @Async // @EnableAsync 설정이 필요합니다.
    protected void executeAsyncRpa() {
        if (activeJobCount.incrementAndGet() > 1) {
            activeJobCount.decrementAndGet();
            return;
        }
        try {
            executeRpaScript();
        } finally {
            activeJobCount.set(0);
        }
    }

    public boolean runInitialCrawlIfNeeded() {
        if (initialCrawlRequested.compareAndSet(false, true)) {
            return runRpaScript();
        }
        return false;
    }

    private void executeRpaScript() {
        String scriptPath = "rpa/scripts/theater_crawler.py";
        String pythonCmd = resolvePythonCommand();

        try {
            log.info("🎬 RPA 스크립트 실행: {} {}", pythonCmd, scriptPath);

            ProcessBuilder pb = new ProcessBuilder(pythonCmd, scriptPath);
            Map<String, String> env = pb.environment();
            env.put("PYTHONIOENCODING", "UTF-8");
            env.put("PYTHONDONTWRITEBYTECODE", "1");

            Process process = pb.start();

            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> readProcessStream(process.getInputStream()));
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> readProcessStream(process.getErrorStream()));

            int exitCode = process.waitFor();
            String output = stdoutFuture.join();
            String errorOutput = stderrFuture.join();

            if (!errorOutput.isBlank()) {
                log.warn("⚠️ RPA stderr: {}", trimLog(errorOutput));
            }

            String jsonOutput = extractJsonArray(output);
            if (exitCode == 0 && !jsonOutput.isBlank()) {
                List<Map<String, Object>> results = objectMapper.readValue(
                        jsonOutput,
                        new TypeReference<List<Map<String, Object>>>() {}
                );
                updateMovies(results);
            } else {
                saveLogToDb("RPA 결과 없음/오류. ExitCode: " + exitCode, "ERROR");
            }
        } catch (IOException | InterruptedException e) {
            log.error("❌ RPA 실행 예외 발생", e);
            saveLogToDb("RPA 예외: " + e.getMessage(), "ERROR");
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    private void updateMovies(List<Map<String, Object>> results) {
        for (Map<String, Object> data : results) {
            try {
                updateMovie(data);
            } catch (Exception e) {
                log.error("❌ 데이터 업데이트 실패: {}", e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void updateMovie(Map<String, Object> data) {
        String title = (String) data.get("title");
        String theaterName = (String) data.get("theater_name");
        String startTime = (String) data.get("start_time");
        String imageUrl = normalizeImageUrl((String) data.get("image_url"));
        
        // 에러 해결: 안정적인 숫자 변환 로직 적용
        Integer totalSeats = toInteger(data.get("total_seats"), defaultTotalSeats(theaterName));
        List<List<?>> availableSeats = (List<List<?>>) data.get("available_seats");

        Set<String> premiumSeatIds = SeatUtils.computePremiumSeatIds(totalSeats, getSeatsPerRowByTheater(theaterName, totalSeats));
        int computedGoodSeats = SeatUtils.countPremiumAvailableSeats(availableSeats, premiumSeatIds);

        String seatsJson;
        try {
            seatsJson = objectMapper.writeValueAsString(availableSeats);
        } catch (Exception e) {
            seatsJson = "[]";
        }

        boolean failedTitle = title != null && title.contains("제목 추출 실패");
        int updatedRows;

        if (failedTitle) {
            String sql = (imageUrl == null) 
                ? "UPDATE movies SET start_time = ?, total_seats = ?, good_seats = ?, available_seats = ? WHERE theater_name = ?"
                : "UPDATE movies SET start_time = ?, total_seats = ?, good_seats = ?, available_seats = ?, image_url = ? WHERE theater_name = ?";
            updatedRows = (imageUrl == null) 
                ? jdbcTemplate.update(sql, startTime, totalSeats, computedGoodSeats, seatsJson, theaterName)
                : jdbcTemplate.update(sql, startTime, totalSeats, computedGoodSeats, seatsJson, imageUrl, theaterName);
        } else {
            String sql = (imageUrl == null) 
                ? "UPDATE movies SET title = ?, start_time = ?, total_seats = ?, good_seats = ?, available_seats = ? WHERE theater_name = ?"
                : "UPDATE movies SET title = ?, start_time = ?, total_seats = ?, good_seats = ?, available_seats = ?, image_url = ? WHERE theater_name = ?";
            updatedRows = (imageUrl == null) 
                ? jdbcTemplate.update(sql, title, startTime, totalSeats, computedGoodSeats, seatsJson, theaterName)
                : jdbcTemplate.update(sql, title, startTime, totalSeats, computedGoodSeats, seatsJson, imageUrl, theaterName);
        }

        if (updatedRows == 0) {
            insertMovie(title, theaterName, startTime, totalSeats, computedGoodSeats, seatsJson, imageUrl);
        }

        log.info("📊 {} 업데이트 완료 (명당: {}석)", theaterName, computedGoodSeats);

        Long movieId = findMovieIdByTheaterName(theaterName);
        if (movieId != null) {
            MovieDto movie = movieRepository.findById(movieId);
            if (movie != null) eventPublisher.publishEvent(new MovieUpdatedEvent(movieId, movie));
        }
    }

    /**
     * [에러 수정] 다양한 타입을 안전하게 Integer로 변환합니다.
     */
    private Integer toInteger(Object value, Integer fallback) {
        if (value == null) return fallback;
        try {
            if (value instanceof Integer) return (Integer) value;
            if (value instanceof Number) return ((Number) value).intValue();
            if (value instanceof String) return Integer.parseInt(((String) value).trim());
        } catch (Exception e) {
            log.warn("⚠️ 숫자 변환 실패: {} (기본값 {} 사용)", value, fallback);
        }
        return fallback;
    }

    private String resolvePythonCommand() {
        String envCmd = System.getenv("PYTHON_COMMAND");
        if (envCmd != null && !envCmd.isBlank()) return envCmd;
        return System.getProperty("os.name").toLowerCase().contains("win") ? "python" : "python3";
    }

    private String readProcessStream(InputStream stream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            return "";
        }
    }

    private String extractJsonArray(String output) {
        if (output == null) return "";
        int start = output.indexOf("[");
        int end = output.lastIndexOf("]");
        return (start != -1 && end > start) ? output.substring(start, end + 1) : "";
    }

    private String trimLog(String value) {
        if (value == null) return "";
        return value.length() > 800 ? value.substring(0, 800) + "..." : value;
    }

    private String normalizeImageUrl(String imageUrl) {
        return (imageUrl == null || imageUrl.isBlank()) ? null : imageUrl.trim();
    }

    private void insertMovie(String title, String theaterName, String startTime, Integer totalSeats,
                             int goodSeats, String seatsJson, String imageUrl) {
        String sql = "INSERT INTO movies (title, theater_name, start_time, total_seats, good_seats, image_url, available_seats) VALUES (?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, title, theaterName, startTime, totalSeats, goodSeats, imageUrl, seatsJson);
    }

    private Long findMovieIdByTheaterName(String theaterName) {
        try {
            return jdbcTemplate.queryForObject("SELECT id FROM movies WHERE theater_name = ?", Long.class, theaterName);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer defaultTotalSeats(String theaterName) {
        if (theaterName == null) return 150;
        return theaterName.contains("롯데") ? 175 : 150;
    }

    private int getSeatsPerRowByTheater(String theaterName, int totalSeats) {
        return (totalSeats > 200 || (theaterName != null && theaterName.contains("롯데"))) ? 25 : 15;
    }

    private void saveLogToDb(String message, String level) {
        try {
            String sql = "INSERT INTO rpa_logs (message, log_level, created_at) VALUES (?, ?, ?)";
            jdbcTemplate.update(sql, message, level, Timestamp.valueOf(LocalDateTime.now(ZoneId.of("Asia/Seoul"))));
        } catch (Exception e) {
            log.error("DB 로그 저장 실패");
        }
    }
}