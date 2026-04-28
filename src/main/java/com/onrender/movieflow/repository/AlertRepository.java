package com.onrender.movieflow.repository;

import com.onrender.movieflow.dto.AlertDto;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class AlertRepository {

    private final JdbcTemplate jdbcTemplate;

    public AlertRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<AlertDto> rowMapper = new RowMapper<>() {
        @Override
        public AlertDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            AlertDto dto = new AlertDto();
            dto.setId(rs.getLong("id"));
            dto.setUserPhone(rs.getString("user_phone"));
            dto.setMovieTitle(rs.getString("movie_title"));
            dto.setRemainingSeats(rs.getInt("remaining_seats"));
            return dto;
        }
    };

    public void insert(AlertDto alert) {
        jdbcTemplate.update(
            "INSERT INTO alerts (user_phone, movie_title, remaining_seats) " +
            "VALUES (?, ?, ?)",
            alert.getUserPhone(),
            alert.getMovieTitle(),
            alert.getRemainingSeats()
        );
    }

    public List<AlertDto> findAll() {
        return jdbcTemplate.query(
            "SELECT id, user_phone, movie_title, remaining_seats FROM alerts",
            rowMapper
        );
    }
}
