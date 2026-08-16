package com.codereview.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ReviewDTO {
    private Long id;
    private Long userId;
    private String language;
    private String sourceType;
    private String codeInput;
    private ReviewResponse reviewResult;
    private String overallRating;
    private int criticalCount;
    private int warningCount;
    private int suggestionCount;
    private LocalDateTime createdAt;
}
