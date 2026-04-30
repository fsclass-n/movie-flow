package com.onrender.movieflow.service;

import com.onrender.movieflow.dto.AlertDto;
import com.onrender.movieflow.repository.AlertRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
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
        log.info("새로운 알림 저장 완료: {}", alertDto.getEmail());
        
        // 비동기 RPA 실행
        new Thread(() -> rpaService.runRpaScript()).start();
    }

    // [중요] 이 메서드가 없으면 컨트롤러에서 에러가 납니다!
    public void deleteAlert(Long id) {
        log.info("알림 삭제 서비스 호출 - ID: {}", id);
        
        // Repository의 삭제 메서드 호출
        alertRepository.deleteById(id); 
        
        log.info("알림 삭제 완료 - ID: {}", id);
    }
}