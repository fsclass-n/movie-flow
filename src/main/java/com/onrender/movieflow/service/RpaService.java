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
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class RpaService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RpaService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void runRpaScript() {
        String pythonCommand = "python"; 
        String scriptPath = "rpa/scripts/theater_crawler.py";

        try {
            log.info("RPA 크롤링 스크립트 실행 시작...");
            
            ProcessBuilder pb = new ProcessBuilder(pythonCommand, scriptPath);
            Map<String, String> env = pb.environment();
            // 파이썬 출력 인코딩 강제 설정 (한글 깨짐 방지)
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
                        new TypeReference<List<Map<String, Object>>>() {}
                );
                updateMovieSeats(results);
            } else {
                // [수정] 파라미터 2개 전달 (메시지, 레벨)
                saveLogToDb("RPA 실행 결과가 없거나 에러 발생. 코드: " + exitCode, "ERROR");
            }

        } catch (IOException | InterruptedException e) {
            // [수정] 파라미터 2개 전달 (메시지, 레벨)
            saveLogToDb("RPA 예외 발생: " + e.getMessage(), "ERROR");
            log.error("RPA 실행 중 예외 발생", e);
            Thread.currentThread().interrupt();
        }
    }

    private void updateMovieSeats(List<Map<String, Object>> results) {
        String updateSql = "UPDATE movies SET good_seats = ? WHERE title = ? AND theater_name = ?";

        for (Map<String, Object> data : results) {
            String title = (String) data.get("title");
            String theaterName = (String) data.get("theater_name");
            Integer goodSeats = (Integer) data.get("good_seats");

            int updatedRows = jdbcTemplate.update(updateSql, goodSeats, title, theaterName);
            
            String logMsg;
            String logLevel;

            if (updatedRows > 0) {
                logMsg = String.format("업데이트 성공: %s - %s (%d석)", theaterName, title, goodSeats);
                logLevel = "INFO";
                log.info(logMsg);
            } else {
                logMsg = String.format("업데이트 대상 없음: %s - %s", theaterName, title);
                logLevel = "WARN";
                log.warn(logMsg);
            }
            
            // [수정] 로그 저장 시 메시지와 레벨을 함께 전달
            saveLogToDb(logMsg, logLevel);
        }
    }

    /**
     * DB의 rpa_logs 테이블에 로그를 저장합니다.
     * log_level 컬럼의 'no default value' 에러를 방지하기 위해 레벨을 함께 저장합니다.
     */
    private void saveLogToDb(String message, String level) {
        try {
            String sql = "INSERT INTO rpa_logs (message, log_level) VALUES (?, ?)";
            jdbcTemplate.update(sql, message, level);
        } catch (Exception e) {
            log.error("로그 DB 저장 실패 (메시지: {}): {}", message, e.getMessage());
        }
    }
}