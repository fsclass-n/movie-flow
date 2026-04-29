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
    private final ObjectMapper objectMapper; // JSON 파싱용

    public RpaService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void runRpaScript() {
        // OS 환경에 따라 "python" 또는 "python3"로 수정이 필요할 수 있습니다.
        String pythonCommand = "python"; 
        String scriptPath = "rpa/scripts/theater_crawler.py";

        try {
            log.info("RPA 크롤링 스크립트 실행 시작...");
            Process process = new ProcessBuilder(pythonCommand, scriptPath).start();

            // 1. 파이썬의 표준 출력(print) 읽기
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
                // 2. 파이썬이 보낸 JSON 데이터를 객체로 변환
                List<Map<String, Object>> results = objectMapper.readValue(
                        output.toString(), 
                        new TypeReference<List<Map<String, Object>>>() {}
                );

                // 3. DB 업데이트 로직 수행
                updateMovieSeats(results);
            } else {
                log.error("RPA 실행 결과가 없거나 에러가 발생했습니다.");
            }

        } catch (IOException | InterruptedException e) {
            log.error("RPA 실행 중 예외 발생", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 크롤링 결과를 순회하며 movies 테이블의 good_seats 컬럼을 업데이트합니다.
     */
    private void updateMovieSeats(List<Map<String, Object>> results) {
        String sql = "UPDATE movies SET good_seats = ? WHERE title = ? AND theater_name = ?";

        for (Map<String, Object> data : results) {
            String title = (String) data.get("title");
            String theaterName = (String) data.get("theater_name");
            Integer goodSeats = (Integer) data.get("good_seats");

            int updatedRows = jdbcTemplate.update(sql, goodSeats, title, theaterName);
            
            if (updatedRows > 0) {
                log.info("DB 업데이트 성공: {} - {} ({}석)", theaterName, title, goodSeats);
            } else {
                log.warn("업데이트 대상 없음: {} - {}", theaterName, title);
            }
        }
    }
}