package com.onrender.movieflow.controller;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminController {
    private final JdbcTemplate jdbcTemplate;

    public AdminController(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @GetMapping("/admin")
    public String admin(Model model) {
        // 간단한 통계 데이터 전달
        model.addAttribute("activeBots", 12); 
        model.addAttribute("sentCount", 142);
        // 로그 테이블에서 최신 로그 10개 가져오기
        List<String> logs = jdbcTemplate.queryForList("SELECT message FROM rpa_logs ORDER BY id DESC LIMIT 10", String.class);
        model.addAttribute("logs", logs);
        return "admin";
    }
}
