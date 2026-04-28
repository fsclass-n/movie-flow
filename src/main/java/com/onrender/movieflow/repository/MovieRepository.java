package com.onrender.movieflow.repository;

import com.onrender.movieflow.dto.MovieDto;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class MovieRepository {

    private final JdbcTemplate jdbcTemplate;

    public MovieRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<MovieDto> rowMapper = new RowMapper<>() {
        @Override
        public MovieDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            MovieDto dto = new MovieDto();
            dto.setId(rs.getLong("id"));
            dto.setTitle(rs.getString("title"));
            dto.setDescription(rs.getString("description"));
            dto.setImageURL(rs.getString("image_url"));
            return dto;
        }
    };

    public List<MovieDto> findAll() {
        return jdbcTemplate.query(
            "SELECT id, title, description, image_url FROM movies ORDER BY id",
            rowMapper
        );
    }
}
