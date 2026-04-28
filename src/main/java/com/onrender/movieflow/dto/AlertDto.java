package com.onrender.movieflow.dto;

import lombok.Data;

@Data
public class AlertDto {
    private Long id;
    private String userPhone;
    private String movieTitle;
    private Integer remainingSeats; // 남은 좌석 조건
}
