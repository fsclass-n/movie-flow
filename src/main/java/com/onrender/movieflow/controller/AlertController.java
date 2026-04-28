package com.onrender.movieflow.controller;

import com.onrender.movieflow.dto.AlertDto;
import com.onrender.movieflow.service.AlertService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping("/alert/save")
    public String saveAlert(@ModelAttribute AlertDto alertDto) {
        alertService.createAlert(alertDto);
        return "redirect:/my_page";
    }
}
