package com.onrender.movieflow.service;

import com.onrender.movieflow.dto.AlertDto;
import com.onrender.movieflow.dto.MovieDto;
import com.onrender.movieflow.repository.AlertRepository;
import com.onrender.movieflow.repository.MovieRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

@Slf4j
@Service
public class AlertService {
    private final AlertRepository alertRepository;
    private final MovieRepository movieRepository;
    private final RpaService rpaService;
    private final JavaMailSender mailSender;

    @Value("${twilio.account.sid:}")
    private String twilioAccountSid;

    @Value("${twilio.auth.token:}")
    private String twilioAuthToken;

    @Value("${twilio.phone.number:}")
    private String twilioPhoneNumber;

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
            log.info("알림 이메일 전송 시도: {}", alertDto.getEmail());
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
            log.error("이메일 전송 실패: {}", e.getMessage(), e);
        }
    }

    private void sendAlertSms(AlertDto alertDto) {
        if (alertDto.getPhone() == null || alertDto.getPhone().isEmpty() ||
            twilioAccountSid == null || twilioAccountSid.isEmpty() ||
            twilioAuthToken == null || twilioAuthToken.isEmpty()) {
            log.info("SMS 설정이 없어 SMS 알림을 건너뜁니다: {}", alertDto.getEmail());
            return;
        }

        try {
            Twilio.init(twilioAccountSid, twilioAuthToken);

            MovieDto movie = movieRepository.findById(alertDto.getMovieId());
            String movieTitle = movie != null ? movie.getTitle() : alertDto.getMovieId() + "번 영화";
            String theaterName = movie != null ? movie.getTheaterName() : "상영관 정보 없음";
            String startTime = movie != null ? movie.getStartTime() : "상영 시간 정보 없음";
            Integer goodSeats = movie != null ? movie.getGoodSeats() : 0;

            String messageBody = "Movie Flow RPA 감시 시작\n" +
                    "영화: " + movieTitle + "\n" +
                    "영화관: " + theaterName + "\n" +
                    "시간: " + startTime + "\n" +
                    "명당 잔여석: " + goodSeats + "석";

            Message message = Message.creator(
                    new PhoneNumber(alertDto.getPhone()),
                    new PhoneNumber(twilioPhoneNumber),
                    messageBody
            ).create();

            log.info("SMS 알림 전송 완료: {} - SID: {}", alertDto.getPhone(), message.getSid());
        } catch (Exception e) {
            log.error("SMS 전송 실패: {}", e.getMessage());
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

    public void sendUpdateAlert(Long movieId, MovieDto movie) {
        List<Map<String, Object>> watchingAlerts = alertRepository.findWatchingAlertsByMovieId(movieId);
        for (Map<String, Object> alert : watchingAlerts) {
            String email = (String) alert.get("email");
            String phone = (String) alert.get("phone");
            try {
                log.info("좌석 업데이트 알림 메일 전송 시도: {}", email);
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(email);
                message.setSubject("Movie Flow - 좌석 업데이트 알림");
                message.setText(
                        "Movie Flow 좌석 정보가 업데이트되었습니다.\n\n" +
                        "영화: " + movie.getTitle() + "\n" +
                        "영화관: " + movie.getTheaterName() + "\n" +
                        "상영 날짜/시간: " + movie.getStartTime() + "\n" +
                        "현재 명당 잔여석: " + movie.getGoodSeats() + "석\n\n" +
                        "좌석 정보를 확인해 보세요."
                );
                mailSender.send(message);
                alertRepository.markAsSent(email, movieId);
                log.info("좌석 업데이트 알림 메일 전송 완료: {}", email);
            } catch (Exception e) {
                log.error("좌석 업데이트 메일 전송 실패: {}", e.getMessage(), e);
            }

            // SMS 알림도 보낼 수 있음 (선택)
            if (phone != null && !phone.isEmpty()) {
                // SMS 로직 추가 가능
            }
        }
    }
}
