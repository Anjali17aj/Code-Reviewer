package com.codereview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewIssue {

    private Integer line;
    private String severity;
    private String category;
    private String message;
    private String suggestion;
}
