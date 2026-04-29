package com.onrender.movieflow.controller;

import com.onrender.movieflow.dto.AlertDto;
import com.onrender.movieflow.service.AlertService;
import com.onrender.movieflow.repository.AlertRepository;
import com.onrender.movieflow.repository.MovieRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class MainController {
    private final MovieRepository movieRepository;
    private final AlertRepository alertRepository;
    private final AlertService alertService;

    public MainController(MovieRepository movieRepository, AlertRepository alertRepository, AlertService alertService) {
        this.movieRepository = movieRepository;
        this.alertRepository = alertRepository;
        this.alertService = alertService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("movies", movieRepository.findAll());
        return "index";
    }

    @GetMapping("/movie/detail/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("movie", movieRepository.findById(id));
        return "movie_detail";
    }

    @GetMapping("/mypage")
    public String myPage(Model model) {
        model.addAttribute("alerts", alertRepository.findAllWithMovie());
        return "mypage";
    }

    @PostMapping("/alert/setup")
    public String setupAlert(@ModelAttribute AlertDto alertDto) {
        alertService.createAlert(alertDto);
        return "redirect:/mypage";
    }
}