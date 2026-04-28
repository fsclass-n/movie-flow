package com.onrender.movieflow.controller;

import com.onrender.movieflow.dto.MovieDto;
import com.onrender.movieflow.dto.AlertDto;
import com.onrender.movieflow.service.AlertService;
import com.onrender.movieflow.repository.MovieRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
public class MainController {

    private final MovieRepository movieRepository;
    private final AlertService alertService;

    public MainController(MovieRepository movieRepository, AlertService alertService) {
        this.movieRepository = movieRepository;
        this.alertService = alertService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("movies", movieRepository.findAll());
        return "index";
    }

    @GetMapping("/my_page")
    public String myPage(Model model) {
        model.addAttribute("alerts", alertService.getAllAlerts());
        model.addAttribute("newAlert", new AlertDto());
        return "my_page";
    }
}
