package com.onrender.movieflow.repository;

import com.onrender.movieflow.dto.AlertDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class AlertRepository {

    private final JdbcTemplate jdbcTemplate;

    public AlertRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 알림 신청 저장
    public void insert(AlertDto alertDto) {
        // DB 컬럼명에 맞춰 수정 (이전 에러 해결용)
        String sql = "INSERT INTO alerts (email, phone, movie_id, user_id, status) VALUES (?, ?, ?, (SELECT id FROM users WHERE email = ? LIMIT 1), 'WATCHING')";
        jdbcTemplate.update(sql, alertDto.getEmail(), alertDto.getPhone(), alertDto.getMovieId(), alertDto.getUserEmail());
    }

    // 마이페이지 목록 조회 (모든 alerts)
    public List<Map<String, Object>> findAllWithMovie() {
        String sql = "SELECT a.id, a.email, a.phone, a.status, m.title AS movie_title, m.theater_name, m.start_time " +
                     "FROM alerts a " +
                     "JOIN movies m ON a.movie_id = m.id " +
                     "ORDER BY a.id DESC";
        return jdbcTemplate.queryForList(sql);
    }
    
    // 사용자별 alerts 조회
    public List<Map<String, Object>> findByUserEmail(String userEmail) {
        String sql = "SELECT a.id, a.email, a.phone, a.status, m.title AS movie_title, m.theater_name, m.start_time " +
                     "FROM alerts a " +
                     "JOIN movies m ON a.movie_id = m.id " +
                     "WHERE a.email = ? " +
                     "ORDER BY a.id DESC";
        return jdbcTemplate.queryForList(sql, userEmail);
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
        return jdbcTemplate.queryForList(sql, movieId);
    }
}
