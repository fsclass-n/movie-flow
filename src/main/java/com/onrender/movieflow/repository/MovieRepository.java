package com.onrender.movieflow.repository;

import com.onrender.movieflow.dto.MovieDto;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MovieRepository {
    private final JdbcTemplate jdbcTemplate;

    public MovieRepository(JdbcTemplate jdbcTemplate) { 
        this.jdbcTemplate = jdbcTemplate; 
    }

    public List<MovieDto> findAll() {
        // 람다식을 메서드 인자에 바로 넣습니다.
        return jdbcTemplate.query("SELECT * FROM movies ORDER BY id", (rs, rowNum) -> {
            MovieDto dto = new MovieDto();
            dto.setId(rs.getLong("id"));
            dto.setTitle(rs.getString("title"));
            dto.setTheaterName(rs.getString("theater_name"));
            dto.setStartTime(rs.getString("start_time"));
            dto.setGoodSeats(rs.getInt("good_seats"));
            return dto;
        });
    }

    public MovieDto findById(Long id) {
        try {
            return jdbcTemplate.queryForObject("SELECT * FROM movies WHERE id = ?", (rs, rowNum) -> {
                MovieDto dto = new MovieDto();
                dto.setId(rs.getLong("id"));
                dto.setTitle(rs.getString("title"));
                dto.setTheaterName(rs.getString("theater_name"));
                dto.setStartTime(rs.getString("start_time"));
                dto.setGoodSeats(rs.getInt("good_seats"));
                return dto;
            }, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}