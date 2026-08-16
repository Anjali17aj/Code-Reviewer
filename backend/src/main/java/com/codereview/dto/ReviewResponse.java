package com.codereview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {

    private String overallAssessment;
    private List<ReviewIssue> issues;
    private String summary;
}
