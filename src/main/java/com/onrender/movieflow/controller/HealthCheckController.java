package com.onrender.movieflow.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthCheckController{

    /**
     * UptimeRobot 잠깨우기(Ping) 전용 엔드포인트
     * 최소한의 자원만 사용하여 200 OK와 'alive' 문자열을 반환합니다.
     */
    @GetMapping("/ping")
    public ResponseEntity<String> ping(){
        // DB 접속이나 가벼운 로그 출력조차 생략하여 메모리/CPU 소모를 최소화합니다.
        return ResponseEntity.ok("alive");
    }
}