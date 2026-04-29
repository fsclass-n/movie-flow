package com.onrender.movieflow.service;

import com.onrender.movieflow.dto.AlertDto;
import com.onrender.movieflow.repository.AlertRepository;
import org.springframework.stereotype.Service;

@Service
public class AlertService {
    private final AlertRepository alertRepository;
    private final RpaService rpaService;

    public AlertService(AlertRepository alertRepository, RpaService rpaService) {
        this.alertRepository = alertRepository;
        this.rpaService = rpaService;
    }

    public void createAlert(AlertDto alertDto) {
        alertRepository.insert(alertDto);
        // 알림 신청 즉시 RPA 봇을 한 번 돌려 확인 시도 (비동기 처리 권장)
        new Thread(() -> rpaService.runRpaScript()).start();
    }
}