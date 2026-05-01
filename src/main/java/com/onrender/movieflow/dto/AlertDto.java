package com.onrender.movieflow.dto;

import lombok.Data;

@Data
public class AlertDto {
    private Long id;
    private Long userId;       // DB 설계 반영
    private Long movieId;      // FK 반영
    private String movieTitle; // 마이페이지 표시용 JOIN 결과
    private String theaterName;
    private String startTime;
    private String email;      // 알림 수신 이메일
    private String phone;      // 알림 수신 전화번호
    private String status;     // WATCHING, SENT 등
}
