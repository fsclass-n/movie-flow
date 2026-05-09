package com.onrender.movieflow.service;

import com.onrender.movieflow.dto.MovieDto;
import com.onrender.movieflow.event.MovieUpdatedEvent;
import com.onrender.movieflow.repository.MovieRepository;
import com.onrender.movieflow.util.SeatUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
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

    public int getActiveJobCount() {
        return activeJobCount.get();
    }

    public boolean runRpaScript() {
        if (activeJobCount.get() > 0) {
            return false;
        }
        activeJobCount.incrementAndGet();
        Thread rpaThread = new Thread(() -> {
            try {
                executeRpaScript();
            } finally {
                activeJobCount.decrementAndGet();
            }
        }, "RpaScriptThread");
        rpaThread.setDaemon(true);
        rpaThread.start();
        return true;
    }

    public boolean runInitialCrawlIfNeeded() {
        if (initialCrawlRequested.compareAndSet(false, true)) {
            return runRpaScript();
        }
        return false;
    }

    private void executeRpaScript() {
        String pythonCommand = "python";
        String scriptPath = "rpa/scripts/theater_crawler.py";

        try {
            log.info("RPA 크롤링 스크립트 실행 시작...");

            ProcessBuilder pb = new ProcessBuilder(resolvePythonCommand(), scriptPath);
            Map<String, String> env = pb.environment();
            env.put("PYTHONIOENCODING", "UTF-8");
            env.put("PYTHONDONTWRITEBYTECODE", "1");

            Process process = pb.start();

            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> readProcessStream(process.getInputStream()));
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> readProcessStream(process.getErrorStream()));

            int exitCode = process.waitFor();
            String output = stdoutFuture.join();
            String errorOutput = stderrFuture.join();
            log.info("RPA 스크립트 종료 코드: {}", exitCode);
            if (!errorOutput.isBlank()) {
                log.warn("RPA stderr: {}", trimLog(errorOutput));
            }

            String jsonOutput = extractJsonArray(output);
            if (exitCode == 0 && !jsonOutput.isBlank()) {
                List<Map<String, Object>> results = objectMapper.readValue(
                        jsonOutput,
                        new TypeReference<List<Map<String, Object>>>() {
                        }
                );
                updateMovies(results);
            } else {
                saveLogToDb("RPA 실행 결과가 없거나 오류가 발생했습니다. 코드: " + exitCode + " / 출력: " + trimLog(output) + " / 오류: " + trimLog(errorOutput), "ERROR");
            }
        } catch (IOException | InterruptedException e) {
            saveLogToDb("RPA 예외 발생: " + e.getMessage(), "ERROR");
            log.error("RPA 실행 중 예외 발생", e);
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    private void updateMovies(List<Map<String, Object>> results) {
        for (Map<String, Object> data : results) {
            try {
                updateMovie(data);
            } catch (Exception e) {
                String theaterName = String.valueOf(data.getOrDefault("theater_name", "알 수 없는 영화관"));
                String message = "영화관 데이터 저장 실패: " + theaterName + " / " + e.getMessage();
                log.error(message, e);
                saveLogToDb(message, "ERROR");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void updateMovie(Map<String, Object> data) {
            String title = (String) data.get("title");
            String theaterName = (String) data.get("theater_name");
            String startTime = (String) data.get("start_time");
            String imageUrl = normalizeImageUrl((String) data.get("image_url"));
            Integer totalSeats = toInteger(data.get("total_seats"), defaultTotalSeats(theaterName));
            List<List<?>> availableSeats = (List<List<?>>) data.get("available_seats");

            Set<String> premiumSeatIds = SeatUtils.computePremiumSeatIds(totalSeats, getSeatsPerRowByTheater(theaterName, totalSeats));
            int computedGoodSeats = SeatUtils.countPremiumAvailableSeats(availableSeats, premiumSeatIds);

            String seatsJson;
            try {
                seatsJson = objectMapper.writeValueAsString(availableSeats);
            } catch (Exception e) {
                seatsJson = "[]";
                log.error("좌석 데이터 JSON 변환 실패: {}", e.getMessage());
            }

            boolean failedTitle = title != null && title.contains("제목 추출 실패");
            String updateSql;
            int updatedRows;
            if (failedTitle) {
                if (imageUrl == null) {
                    updateSql = "UPDATE movies SET start_time = ?, total_seats = ?, good_seats = ?, available_seats = ? WHERE theater_name = ?";
                    updatedRows = jdbcTemplate.update(updateSql, startTime, totalSeats, computedGoodSeats, seatsJson, theaterName);
                } else {
                    updateSql = "UPDATE movies SET start_time = ?, total_seats = ?, good_seats = ?, available_seats = ?, image_url = ? WHERE theater_name = ?";
                    updatedRows = jdbcTemplate.update(updateSql, startTime, totalSeats, computedGoodSeats, seatsJson, imageUrl, theaterName);
                }
            } else {
                if (imageUrl == null) {
                    updateSql = "UPDATE movies SET title = ?, start_time = ?, total_seats = ?, good_seats = ?, available_seats = ? WHERE theater_name = ?";
                    updatedRows = jdbcTemplate.update(updateSql, title, startTime, totalSeats, computedGoodSeats, seatsJson, theaterName);
                } else {
                    updateSql = "UPDATE movies SET title = ?, start_time = ?, total_seats = ?, good_seats = ?, available_seats = ?, image_url = ? WHERE theater_name = ?";
                    updatedRows = jdbcTemplate.update(updateSql, title, startTime, totalSeats, computedGoodSeats, seatsJson, imageUrl, theaterName);
                }
            }

            String logMsg;
            String logLevel;
            if (updatedRows > 0) {
                logMsg = String.format("업데이트 성공: %s - %s / %s / 명당 %d석", theaterName, title, startTime, computedGoodSeats);
                logLevel = "INFO";
                log.info(logMsg);
            } else {
                insertMovie(title, theaterName, startTime, totalSeats, computedGoodSeats, seatsJson, imageUrl);
                logMsg = String.format("신규 영화관 데이터 생성: %s - %s / %s / 명당 %d석", theaterName, title, startTime, computedGoodSeats);
                logLevel = "INFO";
                log.info(logMsg);
            }

            Long movieId = findMovieIdByTheaterName(theaterName);
            if (movieId != null) {
                MovieDto movie = movieRepository.findById(movieId);
                if (movie != null) {
                    eventPublisher.publishEvent(new MovieUpdatedEvent(movieId, movie));
                }
            }
            saveLogToDb(logMsg, logLevel);
    }

    private Integer toInteger(Object value, Integer fallback) {
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return fallback;
    }

    private String readProcessStream(InputStream stream) {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        } catch (IOException e) {
            log.warn("RPA 프로세스 출력 읽기 실패: {}", e.getMessage());
        }
        return output.toString();
    }

    private String resolvePythonCommand() {
        String configured = System.getenv("PYTHON_COMMAND");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? "python" : "python3";
    }

    private String extractJsonArray(String output) {
        if (output == null || output.isBlank()) {
            return "";
        }

        for (int start = 0; start < output.length(); start++) {
            if (output.charAt(start) != '[' || !looksLikeJsonArray(output, start)) {
                continue;
            }

            String candidate = readBalancedArray(output, start);
            if (!candidate.isBlank() && isJsonArray(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private boolean looksLikeJsonArray(String output, int start) {
        for (int index = start + 1; index < output.length(); index++) {
            char ch = output.charAt(index);
            if (!Character.isWhitespace(ch)) {
                return ch == '{' || ch == ']';
            }
        }
        return false;
    }

    private String readBalancedArray(String output, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int index = start; index < output.length(); index++) {
            char ch = output.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }

            if (ch == '"') {
                inString = true;
            } else if (ch == '[') {
                depth++;
            } else if (ch == ']') {
                depth--;
                if (depth == 0) {
                    return output.substring(start, index + 1);
                }
            }
        }
        return "";
    }

    private boolean isJsonArray(String candidate) {
        try {
            return objectMapper.readTree(candidate).isArray();
        } catch (Exception e) {
            return false;
        }
    }

    private String trimLog(String value) {
        if (value == null) {
            return "";
        }
        String compact = value.lines().limit(8).collect(Collectors.joining(" | "));
        return compact.length() > 1000 ? compact.substring(0, 1000) : compact;
    }

    private String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        return imageUrl.trim();
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
        return theaterName != null && theaterName.contains("롯데") ? 175 : 150;
    }

    private int getSeatsPerRowByTheater(String theaterName, int totalSeats) {
        if (totalSeats > 200 || (theaterName != null && theaterName.contains("롯데"))) {
            return 25;
        }
        return 15;
    }

    private void saveLogToDb(String message, String level) {
        try {
            String sql = "INSERT INTO rpa_logs (message, log_level, created_at) VALUES (?, ?, ?)";
            Timestamp createdAt = Timestamp.valueOf(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
            jdbcTemplate.update(sql, message, level, createdAt);
        } catch (Exception e) {
            log.error("로그 DB 저장 실패 (메시지: {}): {}", message, e.getMessage());
        }
    }
}
