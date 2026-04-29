package com.onrender.movieflow.repository;

import com.onrender.movieflow.dto.AlertDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class AlertRepository {
    private final JdbcTemplate jdbcTemplate;

    public AlertRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public void insert(AlertDto alert) {
        jdbcTemplate.update(
            "INSERT INTO alerts (movie_id, email, status) VALUES (?, ?, 'WATCHING')",
            alert.getMovieId(), alert.getEmail()
        );
    }

    public List<AlertDto> findAllWithMovie() {
        String sql = "SELECT a.id, m.title as movie_title, m.theater_name, m.start_time, a.email, a.status " +
                     "FROM alerts a JOIN movies m ON a.movie_id = m.id ORDER BY a.id DESC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            AlertDto dto = new AlertDto();
            dto.setId(rs.getLong("id"));
            dto.setMovieTitle(rs.getString("movie_title"));
            dto.setTheaterName(rs.getString("theater_name"));
            dto.setStartTime(rs.getString("start_time"));
            dto.setEmail(rs.getString("email"));
            dto.setStatus(rs.getString("status"));
            return dto;
        });
    }
}