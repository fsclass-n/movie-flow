package com.onrender.movieflow.dto;

import lombok.Data;

@Data
public class MovieDto {
    private Long id;
    private String title;
    private String theaterName;
    private String startTime;
    private Integer totalSeats;
    private Integer goodSeats;
    private String availableSeats;
}
