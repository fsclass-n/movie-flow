package com.onrender.movieflow.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onrender.movieflow.dto.MovieDto;
import com.onrender.movieflow.repository.AlertRepository;
import com.onrender.movieflow.repository.MovieRepository;
import com.onrender.movieflow.service.RpaService;
import com.onrender.movieflow.util.SeatUtils;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequiredArgsConstructor
public class MainController {

    private final MovieRepository movieRepository;
    private final AlertRepository alertRepository;
    private final RpaService rpaService;
    private final ObjectMapper objectMapper;

    @ModelAttribute("activeBots")
    public int activeBots() {
        return rpaService.getActiveJobCount();
    }

    @GetMapping("/")
    public String index(Model model) {
        boolean initialCrawlStarted = rpaService.runInitialCrawlIfNeeded();
        model.addAttribute("initialCrawlStarted", initialCrawlStarted);

        List<MovieDto> movies = movieRepository.findAll();
        movies.forEach(this::refreshComputedGoodSeats);
        model.addAttribute("movies", movies);
        return "index";
    }

    @GetMapping("/api/movies")
    @ResponseBody
    public List<MovieDto> fetchMovies() {
        List<MovieDto> movies = movieRepository.findAll();
        movies.forEach(this::refreshComputedGoodSeats);
        return movies;
    }

    @GetMapping("/movie/detail/{id}")
    public String detail(@PathVariable("id") Long id, Model model) {
        MovieDto movie = movieRepository.findById(id);
        List<String> goodSeats = new ArrayList<>();
        List<String> availableSeatIds = new ArrayList<>();
        List<String> premiumSeatIds = new ArrayList<>();

        int totalSeats = getTotalSeats(movie);
        int seatsPerRow = getSeatsPerRow(movie, totalSeats);
        List<String> seatRows = getSeatRows(totalSeats, seatsPerRow);

        Set<String> premiumSeatSet = SeatUtils.computePremiumSeatIds(totalSeats, seatsPerRow);
        premiumSeatIds.addAll(premiumSeatSet);

        int displayedGoodSeatCount = 0;
        if (movie != null && movie.getAvailableSeats() != null) {
            try {
                List<List<?>> seats = objectMapper.readValue(movie.getAvailableSeats(), new TypeReference<List<List<?>>>() {});
                for (List<?> seat : seats) {
                    if (seat.size() == 2) {
                        String row = seat.get(0).toString();
                        String col = seat.get(1).toString();
                        String seatId = row + col;
                        availableSeatIds.add(seatId);
                        if (premiumSeatSet.contains(seatId)) {
                            goodSeats.add(seatId);
                        }
                    }
                }
                displayedGoodSeatCount = goodSeats.size();
                model.addAttribute("availableSeatsList", seats);
            } catch (Exception e) {
                model.addAttribute("availableSeatsList", List.of());
            }
        } else {
            model.addAttribute("availableSeatsList", List.of());
        }

        if (movie != null) {
            movie.setGoodSeats(displayedGoodSeatCount);
        }

        model.addAttribute("goodSeats", goodSeats);
        model.addAttribute("displayGoodSeatCount", displayedGoodSeatCount);
        model.addAttribute("availableSeatIds", availableSeatIds);
        model.addAttribute("premiumSeatIds", premiumSeatIds);
        model.addAttribute("seatRows", seatRows);
        model.addAttribute("seatsPerRow", seatsPerRow);
        model.addAttribute("seatTotalSeats", totalSeats);
        model.addAttribute("movie", movie);
        return "movie_detail";
    }

    @GetMapping("/mypage")
    public String myPage(Model model, HttpSession session) {
        String currentUserEmail = (String) session.getAttribute("userEmail");
        if (currentUserEmail == null || currentUserEmail.isEmpty()) {
            model.addAttribute("alerts", List.of());
            return "mypage";
        }

        List<Map<String, Object>> alerts = alertRepository.findByUserEmail(currentUserEmail);
        if (alerts == null || alerts.isEmpty()) {
            session.removeAttribute("userEmail");
            session.removeAttribute("alertCreated");
            model.addAttribute("alerts", List.of());
        } else {
            model.addAttribute("alerts", alerts);
        }
        return "mypage";
    }

    // --- Private Helper Methods (동작 유지) ---

    private void refreshComputedGoodSeats(MovieDto movie) {
        if (movie == null) return;
        int totalSeats = getTotalSeats(movie);
        int seatsPerRow = getSeatsPerRow(movie, totalSeats);
        Set<String> premiumSeatIds = SeatUtils.computePremiumSeatIds(totalSeats, seatsPerRow);
        List<String> availableSeatIds = parseAvailableSeatIds(movie.getAvailableSeats());
        int computedGoodSeats = (int) availableSeatIds.stream().filter(premiumSeatIds::contains).count();
        movie.setGoodSeats(computedGoodSeats);
    }

    private List<String> parseAvailableSeatIds(String availableSeatsJson) {
        if (availableSeatsJson == null || availableSeatsJson.isBlank()) return List.of();
        try {
            List<List<?>> seats = objectMapper.readValue(availableSeatsJson, new TypeReference<List<List<?>>>() {});
            List<String> seatIds = new ArrayList<>();
            for (List<?> seat : seats) {
                if (seat.size() == 2) {
                    seatIds.add(seat.get(0).toString() + seat.get(1).toString());
                }
            }
            return seatIds;
        } catch (Exception e) {
            return List.of();
        }
    }

    private boolean isLotteTheater(MovieDto movie) {
        return movie != null && movie.getTheaterName() != null && movie.getTheaterName().contains("롯데");
    }

    private int getTotalSeats(MovieDto movie) {
        if (movie != null && movie.getTotalSeats() != null && movie.getTotalSeats() > 0) {
            return movie.getTotalSeats();
        }
        return isLotteTheater(movie) ? 175 : 150;
    }

    private int getSeatsPerRow(MovieDto movie, int totalSeats) {
        if (totalSeats > 200 || isLotteTheater(movie)) return 25;
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
}