package com.onrender.movieflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class RpaService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AtomicInteger activeJobCount = new AtomicInteger(0);
    private final AtomicBoolean initialCrawlRequested = new AtomicBoolean(false);

    public RpaService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
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

            ProcessBuilder pb = new ProcessBuilder(pythonCommand, scriptPath);
            Map<String, String> env = pb.environment();
            env.put("PYTHONIOENCODING", "UTF-8");

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            int exitCode = process.waitFor();
            log.info("RPA 스크립트 종료 코드: {}", exitCode);

            if (exitCode == 0 && output.length() > 0) {
                List<Map<String, Object>> results = objectMapper.readValue(
                        output.toString(),
                        new TypeReference<List<Map<String, Object>>>() {
                        }
                );
                updateMovies(results);
            } else {
                saveLogToDb("RPA 실행 결과가 없거나 오류가 발생했습니다. 코드: " + exitCode, "ERROR");
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
            String title = (String) data.get("title");
            String theaterName = (String) data.get("theater_name");
            String startTime = (String) data.get("start_time");
            Integer goodSeats = toInteger(data.get("good_seats"), 0);
            Integer totalSeats = toInteger(data.get("total_seats"), defaultTotalSeats(theaterName));
            List<List<Object>> availableSeats = (List<List<Object>>) data.get("available_seats");

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
                updateSql = "UPDATE movies SET start_time = ?, total_seats = ?, good_seats = ?, available_seats = ? WHERE theater_name = ?";
                updatedRows = jdbcTemplate.update(updateSql, startTime, totalSeats, goodSeats, seatsJson, theaterName);
            } else {
                updateSql = "UPDATE movies SET title = ?, start_time = ?, total_seats = ?, good_seats = ?, available_seats = ? WHERE theater_name = ?";
                updatedRows = jdbcTemplate.update(updateSql, title, startTime, totalSeats, goodSeats, seatsJson, theaterName);
            }

            String logMsg;
            String logLevel;
            if (updatedRows > 0) {
                logMsg = String.format("업데이트 성공: %s - %s / %s / 명당 %d석", theaterName, title, startTime, goodSeats);
                logLevel = "INFO";
                log.info(logMsg);
            } else {
                logMsg = String.format("업데이트 대상 없음: %s - %s", theaterName, title);
                logLevel = "WARN";
                log.warn(logMsg);
            }
            saveLogToDb(logMsg, logLevel);
        }
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

    private Integer defaultTotalSeats(String theaterName) {
        return theaterName != null && theaterName.contains("롯데") ? 175 : 150;
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
