package com.onrender.movieflow.dto;

import lombok.Data;

@Data
public class MovieDto {
    private Long id;
    private String title;
    private String theaterName; // 추가
    private String startTime;   // 추가
    private Integer goodSeats;  // 추가 (명당 잔여석)
}
