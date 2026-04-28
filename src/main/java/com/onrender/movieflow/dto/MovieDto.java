package com.onrender.movieflow.dto;

import lombok.Data;

@Data
public class MovieDto {
    private Long id;
    private String title;
    private String description;
    private String imageURL;
}
