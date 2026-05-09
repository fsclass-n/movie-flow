package com.onrender.movieflow.repository;

import com.onrender.movieflow.dto.AlertDto;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

@Repository
@Slf4j
public class AlertRepository {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public AlertRepository(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void ensureOptionalColumns() {
        if (!columnExists("alerts", "phone")) {
            try {
                jdbcTemplate.execute("ALTER TABLE alerts ADD COLUMN phone VARCHAR(20)");
            } catch (Exception e) {
                log.warn("alerts.phone 컬럼 자동 추가 실패. phone 없이 계속 동작합니다: {}", e.getMessage());
            }
        }
    }

    // 알림 신청 저장
    public void insert(AlertDto alertDto) {
        String sql = "INSERT INTO alerts (email, phone, movie_id, user_id, status) VALUES (?, ?, ?, (SELECT id FROM users WHERE email = ? LIMIT 1), 'WATCHING')";
        try {
            jdbcTemplate.update(sql, alertDto.getEmail(), alertDto.getPhone(), alertDto.getMovieId(), alertDto.getUserEmail());
        } catch (BadSqlGrammarException e) {
            String fallbackSql = "INSERT INTO alerts (email, movie_id, user_id, status) VALUES (?, ?, (SELECT id FROM users WHERE email = ? LIMIT 1), 'WATCHING')";
            jdbcTemplate.update(fallbackSql, alertDto.getEmail(), alertDto.getMovieId(), alertDto.getUserEmail());
        }
    }

    // 마이페이지 목록 조회 (모든 alerts)
    public List<Map<String, Object>> findAllWithMovie() {
        String sql = "SELECT a.id, a.email, a.phone, a.status, m.title AS movie_title, m.theater_name, m.start_time " +
                     "FROM alerts a " +
                     "JOIN movies m ON a.movie_id = m.id " +
                     "ORDER BY a.id DESC";
        try {
            return jdbcTemplate.queryForList(sql);
        } catch (BadSqlGrammarException e) {
            return jdbcTemplate.queryForList(sqlWithoutPhone("ORDER BY a.id DESC"));
        }
    }
    
    // 사용자별 alerts 조회
    public List<Map<String, Object>> findByUserEmail(String userEmail) {
        String sql = "SELECT a.id, a.email, a.phone, a.status, m.title AS movie_title, m.theater_name, m.start_time " +
                     "FROM alerts a " +
                     "JOIN movies m ON a.movie_id = m.id " +
                     "WHERE a.email = ? " +
                     "ORDER BY a.id DESC";
        try {
            return jdbcTemplate.queryForList(sql, userEmail);
        } catch (BadSqlGrammarException e) {
            return jdbcTemplate.queryForList(sqlWithoutPhone("WHERE a.email = ? ORDER BY a.id DESC"), userEmail);
        }
    }

    // [중요] 이 메서드가 없어서 AlertService에서 에러가 났던 것입니다!
    public void deleteById(Long id) {
        String sql = "DELETE FROM alerts WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public void markAsSent(String email, Long movieId) {
        String sql = "UPDATE alerts SET status = 'SENT' WHERE email = ? AND movie_id = ?";
        jdbcTemplate.update(sql, email, movieId);
    }

    public List<Map<String, Object>> findWatchingAlertsByMovieId(Long movieId) {
        String sql = "SELECT id, email, phone FROM alerts WHERE movie_id = ? AND status = 'WATCHING'";
        try {
            return jdbcTemplate.queryForList(sql, movieId);
        } catch (BadSqlGrammarException e) {
            return jdbcTemplate.queryForList("SELECT id, email FROM alerts WHERE movie_id = ? AND status = 'WATCHING'", movieId);
        }
    }

    private String sqlWithoutPhone(String tail) {
        return "SELECT a.id, a.email, a.status, m.title AS movie_title, m.theater_name, m.start_time " +
                "FROM alerts a JOIN movies m ON a.movie_id = m.id " + tail;
    }

    private boolean columnExists(String tableName, String columnName) {
        try (Connection connection = dataSource.getConnection();
             ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, tableName, columnName)) {
            return columns.next();
        } catch (Exception e) {
            log.warn("컬럼 존재 여부 확인 실패: {}.{} - {}", tableName, columnName, e.getMessage());
            return true;
        }
    }
}
