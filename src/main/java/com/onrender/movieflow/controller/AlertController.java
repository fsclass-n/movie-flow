package com.onrender.movieflow.controller;

import com.onrender.movieflow.dto.AlertDto;
import com.onrender.movieflow.service.AlertService;
import com.onrender.movieflow.service.RpaService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/alert")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;
    private final RpaService rpaService;

    @PostMapping("/setup")
    public String setupAlert(@ModelAttribute AlertDto alertDto, HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            String currentUserEmail = (String) session.getAttribute("userEmail");
            if (currentUserEmail == null || currentUserEmail.isEmpty()) {
                currentUserEmail = alertDto.getEmail();
                session.setAttribute("userEmail", currentUserEmail);
            }
            session.setAttribute("alertCreated", true);
            alertDto.setUserEmail(currentUserEmail);

            // 알림 생성 및 RPA 스크립트 실행
            alertService.createAlert(alertDto);
            rpaService.runRpaScript();

            redirectAttributes.addFlashAttribute("message", "알림 설정이 완료되었습니다.");
        } catch (Exception e) {
            log.error("알림 설정 중 오류 발생", e);
            redirectAttributes.addFlashAttribute("error", "알림 설정에 실패했습니다.");
        }
        return "redirect:/mypage";
    }

    @PostMapping("/cancel")
    public String cancelAlert(@RequestParam("id") Long id) {
        alertService.deleteAlert(id);
        return "redirect:/mypage";
    }
}