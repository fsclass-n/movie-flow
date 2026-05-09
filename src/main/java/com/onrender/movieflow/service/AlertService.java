package com.onrender.movieflow.service;

import com.onrender.movieflow.dto.AlertDto;
import com.onrender.movieflow.dto.MovieDto;
import com.onrender.movieflow.event.MovieUpdatedEvent;
import com.onrender.movieflow.repository.AlertRepository;
import com.onrender.movieflow.repository.MovieRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AlertService {
    private final AlertRepository alertRepository;
    private final MovieRepository movieRepository;

    public AlertService(AlertRepository alertRepository, MovieRepository movieRepository) {
        this.alertRepository = alertRepository;
        this.movieRepository = movieRepository;
    }

    /**
     * 알림 생성 시 호출 (감시 시작 메일 발송)
     */
    public void createAlert(AlertDto alertDto) {
        alertRepository.insert(alertDto);
        log.info("새로운 알림 저장 완료: {}", alertDto.getEmail());

        MovieDto movie = movieRepository.findById(alertDto.getMovieId());
        String movieTitle = (movie != null) ? movie.getTitle() : "신청 영화";

        // 파이썬 스크립트를 호출하여 시작 메일 전송
        String subject = "[Movie Flow] RPA 감시 시작 알림";
        String body = String.format(
            "안녕하세요.\n\n'%s' 영화에 대한 실시간 좌석 감시가 시작되었습니다.\n" +
            "명당 좌석이 발견되면 즉시 다시 메일을 보내드리겠습니다.", movieTitle
        );
        
        executePythonSendMail(alertDto.getEmail(), subject, body);
    }

    /**
     * RPA 스크립트에 의해 영화 정보 업데이트 시 호출
     */
    @EventListener
    public void handleMovieUpdated(MovieUpdatedEvent event) {
        sendUpdateAlert(event.getMovieId(), event.getMovie());
    }

    public void sendUpdateAlert(Long movieId, MovieDto movie) {
        List<Map<String, Object>> watchingAlerts = alertRepository.findWatchingAlertsByMovieId(movieId);
        
        for (Map<String, Object> alert : watchingAlerts) {
            String email = (String) alert.get("email");
            String subject = "[Movie Flow] 좌석 업데이트 알림";
            String body = String.format(
                "Movie Flow 좌석 정보가 업데이트되었습니다.\n\n" +
                "영화: %s\n" +
                "영화관: %s\n" +
                "상영시간: %s\n" +
                "현재 명당 잔여석: %d석\n\n" +
                "지금 바로 예매 사이트에서 확인해 보세요!",
                movie.getTitle(), movie.getTheaterName(), movie.getStartTime(), movie.getGoodSeats()
            );

            executePythonSendMail(email, subject, body);
            alertRepository.markAsSent(email, movieId);
        }
    }

    /**
     * [핵심] 외부 파이썬 스크립트(send.py) 실행
     */
    private void executePythonSendMail(String recipient, String subject, String body) {
        try {
            log.info("파이썬 메일러 호출: {}", recipient);

            // 운영체제 확인 (윈도우는 python, 리눅스/맥은 python3 인 경우가 많음)
            String pythonCmd = System.getProperty("os.name").toLowerCase().contains("win") ? "python" : "python3";

            // ProcessBuilder 구성 (인자 전달: 수신자, 제목, 본문)
            ProcessBuilder pb = new ProcessBuilder(
                    pythonCmd, 
                    "rpa/scripts/send.py", // 파일 경로가 프로젝트 루트 기준인지 확인 필수
                    recipient, 
                    subject, 
                    body
            );
            
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 파이썬의 print() 출력을 자바 로그에 기록 (인코딩 UTF-8 명시)
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[Python] {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("메일 전송 프로세스 종료 성공");
            } else {
                log.error("메일 전송 프로세스 종료 실패 (Exit Code: {})", exitCode);
            }

        } catch (Exception e) {
            log.error("파이썬 실행 중 예외 발생: {}", e.getMessage());
        }
    }

    public void deleteAlert(Long id) {
        alertRepository.deleteById(id);
    }
}