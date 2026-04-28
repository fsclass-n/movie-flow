package com.onrender.movieflow.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.IOException;

@Service
@Slf4j
public class RpaService {

    public void runRpaScript() {
        String scriptPath = "rpa/main.py";
        try {
            Process process = new ProcessBuilder("python", scriptPath).start();
            int exitCode = process.waitFor();
            log.info("RPA 스크립트 종료 코드: " + exitCode);
        } catch (IOException | InterruptedException e) {
            log.error("RPA 실행 실패", e);
        }
    }
}
