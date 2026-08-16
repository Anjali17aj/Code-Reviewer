package com.codereview.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class PRReviewRequest {
    @NotBlank
    private String owner;
    @NotBlank
    private String repo;
    private int prNumber;
}
