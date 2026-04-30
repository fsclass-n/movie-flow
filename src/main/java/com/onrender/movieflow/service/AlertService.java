package com.onrender.movieflow.service;

import com.onrender.movieflow.dto.AlertDto;
import com.onrender.movieflow.dto.MovieDto;
import com.onrender.movieflow.repository.AlertRepository;
import com.onrender.movieflow.repository.MovieRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AlertService {
    private final AlertRepository alertRepository;
    private final MovieRepository movieRepository;
    private final RpaService rpaService;
    private final JavaMailSender mailSender;

    public AlertService(AlertRepository alertRepository, MovieRepository movieRepository,
                        RpaService rpaService, JavaMailSender mailSender) {
        this.alertRepository = alertRepository;
        this.movieRepository = movieRepository;
        this.rpaService = rpaService;
        this.mailSender = mailSender;
    }

    public void createAlert(AlertDto alertDto) {
        alertRepository.insert(alertDto);
        log.info("새로운 알림 저장 완료: {}", alertDto.getEmail());

        rpaService.runRpaScript();
        sendAlertEmail(alertDto);
    }

    private void sendAlertEmail(AlertDto alertDto) {
        try {
            MovieDto movie = movieRepository.findById(alertDto.getMovieId());
            String movieTitle = movie != null ? movie.getTitle() : alertDto.getMovieId() + "번 영화";
            String theaterName = movie != null ? movie.getTheaterName() : "상영관 정보 없음";
            String startTime = movie != null ? movie.getStartTime() : "상영 시간 정보 없음";
            Integer goodSeats = movie != null ? movie.getGoodSeats() : 0;

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(alertDto.getEmail());
            message.setSubject("Movie Flow - RPA 감시 시작 알림");
            message.setText(
                    "Movie Flow RPA 감시가 시작되었습니다.\n\n" +
                    "영화: " + movieTitle + "\n" +
                    "영화관: " + theaterName + "\n" +
                    "상영 날짜/시간: " + startTime + "\n" +
                    "현재 명당 잔여석: " + goodSeats + "석\n\n" +
                    "좌석 정보가 갱신되면 이 메일 주소로 알림을 보내드리겠습니다."
            );
            mailSender.send(message);
            alertRepository.markAsSent(alertDto.getEmail(), alertDto.getMovieId());
            log.info("알림 이메일 전송 완료: {}", alertDto.getEmail());
        } catch (Exception e) {
            log.error("이메일 전송 실패: {}", e.getMessage());
        }
    }

    public void runRpa() {
        rpaService.runRpaScript();
    }

    public void deleteAlert(Long id) {
        log.info("알림 삭제 요청 - ID: {}", id);
        alertRepository.deleteById(id);
        log.info("알림 삭제 완료 - ID: {}", id);
    }
}
