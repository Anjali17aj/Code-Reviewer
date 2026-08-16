package com.codereview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Wraps the LLM response for multi-file analysis.
 * Contains both the overall ReviewResponse and per-file summaries.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultiFileAnalysisResult {

    private ReviewResponse reviewResponse;

    /**
     * Per-file summaries keyed by file name.
     * The LLM returns this as part of its JSON response for multi-file reviews.
     */
    private Map<String, FileLevelSummary> fileReviews;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileLevelSummary {
        private String assessment;
        private int issueCount;
    }
}
