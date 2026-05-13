package com.onrender.movieflow.controller;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.onrender.movieflow.service.RpaService;

@Controller
public class AdminController {
    private final JdbcTemplate jdbcTemplate;
    private final RpaService rpaService;

    public AdminController(JdbcTemplate jdbcTemplate, RpaService rpaService) {
        this.jdbcTemplate = jdbcTemplate;
        this.rpaService = rpaService;
    }

    @GetMapping("/admin")
    public String admin(Model model) {
        Integer activeBots = rpaService.getActiveJobCount();
        Integer sentCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM alerts WHERE status = 'SENT'", Integer.class);
        List<java.util.Map<String, Object>> logs = jdbcTemplate.queryForList("SELECT message, log_level, created_at FROM rpa_logs ORDER BY id DESC LIMIT 15");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        ZoneId seoulZone = ZoneId.of("Asia/Seoul");
        for (java.util.Map<String, Object> log : logs) {
            Object createdAt = log.get("created_at");
            String formatted = "";
            if (createdAt instanceof Timestamp) {
                formatted = ((Timestamp) createdAt).toLocalDateTime().format(formatter);
            } else if (createdAt instanceof LocalDateTime) {
                formatted = ((LocalDateTime) createdAt).format(formatter);
            } else {
                formatted = java.time.ZonedDateTime.now(seoulZone).format(formatter);
            }
            log.put("createdAtFormatted", formatted);
        }

        model.addAttribute("activeBots", activeBots != null ? activeBots : 0);
        model.addAttribute("sentCount", sentCount != null ? sentCount : 0);
        model.addAttribute("logs", logs);
        model.addAttribute("currentSeoulTime", java.time.ZonedDateTime.now(seoulZone).format(formatter));
        return "admin";
    }

    @GetMapping("/api/rpa/status")
    @ResponseBody
    public Map<String, Object> rpaStatus() {
        int activeBots = rpaService.getActiveJobCount();
        return Map.of(
                "activeBots", activeBots,
                "active", activeBots > 0
        );
    }
}
