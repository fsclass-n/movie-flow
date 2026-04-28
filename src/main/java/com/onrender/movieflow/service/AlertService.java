package com.onrender.movieflow.service;

import com.onrender.movieflow.dto.AlertDto;
import com.onrender.movieflow.repository.AlertRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AlertService {

    private final AlertRepository alertRepository;
    private final RpaService rpaService;

    public AlertService(AlertRepository alertRepository, RpaService rpaService) {
        this.alertRepository = alertRepository;
        this.rpaService = rpaService;
    }

    public List<AlertDto> getAllAlerts() {
        return alertRepository.findAll();
    }

    public void createAlert(AlertDto alertDto) {
        alertRepository.insert(alertDto);
        rpaService.runRpaScript();
    }
}
