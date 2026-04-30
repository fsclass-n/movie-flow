package com.onrender.movieflow.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onrender.movieflow.dto.AlertDto;
import com.onrender.movieflow.repository.AlertRepository;
import com.onrender.movieflow.repository.MovieRepository;
import com.onrender.movieflow.service.AlertService;
import com.onrender.movieflow.service.RpaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;

@Controller
public class MainController {
    private final MovieRepository movieRepository;
    private final AlertRepository alertRepository;
    private final AlertService alertService;
    private final RpaService rpaService;
    private final ObjectMapper objectMapper;

    public MainController(MovieRepository movieRepository, AlertRepository alertRepository, AlertService alertService,
                          RpaService rpaService, ObjectMapper objectMapper) {
        this.movieRepository = movieRepository;
        this.alertRepository = alertRepository;
        this.alertService = alertService;
        this.rpaService = rpaService;
        this.objectMapper = objectMapper;
    }

    @ModelAttribute("activeBots")
    public int activeBots() {
        return rpaService.getActiveJobCount();
    }

    @GetMapping("/")
    public String index(Model model) {
        rpaService.runInitialCrawlIfNeeded();
        model.addAttribute("movies", movieRepository.findAll());
        return "index";
    }

    @GetMapping("/movie/detail/{id}")
    public String detail(@PathVariable("id") Long id, Model model) {
        var movie = movieRepository.findById(id);
        List<String> goodSeats = new ArrayList<>();
        List<String> availableSeatIds = new ArrayList<>();
        List<String> premiumSeatIds = new ArrayList<>();

        int totalSeats = getTotalSeats(movie);
        int seatsPerRow = getSeatsPerRow(movie, totalSeats);
        List<String> seatRows = getSeatRows(totalSeats, seatsPerRow);
        for (String row : List.of("C", "D", "E")) {
            for (int col = premiumStart(seatsPerRow); col <= premiumEnd(seatsPerRow); col++) {
                premiumSeatIds.add(row + col);
            }
        }

        if (movie != null && movie.getAvailableSeats() != null) {
            try {
                List<List<?>> seats = objectMapper.readValue(movie.getAvailableSeats(), new TypeReference<List<List<?>>>() {
                });
                for (List<?> seat : seats) {
                    if (seat.size() == 2) {
                        String row = seat.get(0).toString();
                        String col = seat.get(1).toString();
                        String seatId = row + col;
                        availableSeatIds.add(seatId);
                        if (isPremiumSeat(row, col, seatsPerRow)) {
                            goodSeats.add(seatId);
                        }
                    }
                }
                model.addAttribute("availableSeatsList", seats);
            } catch (Exception e) {
                model.addAttribute("availableSeatsList", List.of());
            }
        } else {
            model.addAttribute("availableSeatsList", List.of());
        }

        model.addAttribute("goodSeats", goodSeats);
        model.addAttribute("availableSeatIds", availableSeatIds);
        model.addAttribute("premiumSeatIds", premiumSeatIds);
        model.addAttribute("seatRows", seatRows);
        model.addAttribute("seatsPerRow", seatsPerRow);
        model.addAttribute("seatTotalSeats", totalSeats);
        model.addAttribute("movie", movie);
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

    @PostMapping("/alert/cancel")
    public String cancelAlert(@RequestParam("id") Long id) {
        alertService.deleteAlert(id);
        return "redirect:/mypage";
    }

    private boolean isPremiumSeat(String row, String col, int seatsPerRow) {
        try {
            int seatNumber = Integer.parseInt(col);
            return List.of("C", "D", "E").contains(row)
                    && seatNumber >= premiumStart(seatsPerRow)
                    && seatNumber <= premiumEnd(seatsPerRow);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isLotteTheater(com.onrender.movieflow.dto.MovieDto movie) {
        return movie != null && movie.getTheaterName() != null && movie.getTheaterName().contains("롯데");
    }

    private int getTotalSeats(com.onrender.movieflow.dto.MovieDto movie) {
        if (movie != null && movie.getTotalSeats() != null && movie.getTotalSeats() > 0) {
            return movie.getTotalSeats();
        }
        return isLotteTheater(movie) ? 175 : 150;
    }

    private int getSeatsPerRow(com.onrender.movieflow.dto.MovieDto movie, int totalSeats) {
        if (totalSeats > 200 || isLotteTheater(movie)) {
            return 25;
        }
        return 15;
    }

    private List<String> getSeatRows(int totalSeats, int seatsPerRow) {
        int rowCount = (int) Math.ceil(totalSeats / (double) seatsPerRow);
        List<String> rows = new ArrayList<>();
        for (int index = 0; index < Math.min(rowCount, 26); index++) {
            rows.add(String.valueOf((char) ('A' + index)));
        }
        return rows;
    }

    private int premiumStart(int seatsPerRow) {
        return Math.max(1, Math.round((seatsPerRow + 1) / 2.0f - 2));
    }

    private int premiumEnd(int seatsPerRow) {
        return Math.min(seatsPerRow, Math.round((seatsPerRow + 1) / 2.0f + 3));
    }
}
