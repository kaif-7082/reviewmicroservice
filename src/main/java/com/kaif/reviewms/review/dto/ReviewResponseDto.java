package com.kaif.reviewms.review.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewResponseDto {
    private Long id;
    private String title;
    private String description;
    private double rating;
}