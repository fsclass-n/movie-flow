package com.onrender.movieflow.controller;

import com.onrender.movieflow.dto.AlertDto;
import com.onrender.movieflow.service.AlertService;
import com.onrender.movieflow.repository.AlertRepository;
import com.onrender.movieflow.repository.MovieRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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
    // 수정: @PathVariable("id") 처럼 이름을 명시해야 합니다.
    public String detail(@PathVariable("id") Long id, Model model) {
        model.addAttribute("movie", movieRepository.findById(id));
        return "movie_detail";
    }

    @GetMapping("/mypage")
    public String myPage(Model model) {
        model.addAttribute("alerts", alertRepository.findAllWithMovie());
        return "mypage";
    }

    @PostMapping("/alert/setup")
    // 수정: @ModelAttribute("id")를 제거하거나 @ModelAttribute("alertDto")로 변경해야 합니다.
    // 폼 데이터를 객체로 받을 때는 이름을 명시하지 않아도 타입으로 매핑됩니다.
    public String setupAlert(@ModelAttribute AlertDto alertDto) {
        alertService.createAlert(alertDto);
        return "redirect:/mypage";
    }

    @PostMapping("/alert/cancel")
    public String cancelAlert(@RequestParam("id") Long id) { // 파라미터 이름 "id" 명시 필수!
        alertService.deleteAlert(id);
        return "redirect:/mypage";
    }
}