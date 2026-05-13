package com.onrender.movieflow.event;

import com.onrender.movieflow.dto.MovieDto;

public class MovieUpdatedEvent {
    private final Long movieId;
    private final MovieDto movie;

    public MovieUpdatedEvent(Long movieId, MovieDto movie) {
        this.movieId = movieId;
        this.movie = movie;
    }

    public Long getMovieId() {
        return movieId;
    }

    public MovieDto getMovie() {
        return movie;
    }
}
