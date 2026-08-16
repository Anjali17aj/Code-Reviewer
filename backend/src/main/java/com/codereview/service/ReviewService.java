package com.codereview.service;

import com.codereview.dto.*;
import com.codereview.entity.CodeFile;
import com.codereview.entity.Review;
import com.codereview.entity.ReviewFile;
import com.codereview.repository.CodeFileRepository;
import com.codereview.repository.ReviewFileRepository;
import com.codereview.repository.ReviewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final LLMService llmService;
    private final ReviewRepository reviewRepository;
    private final ReviewFileRepository reviewFileRepository;
    private final CodeFileRepository codeFileRepository;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    private static final String REVIEW_CACHE_PREFIX = "review:";

    /**
     * Analyze code and save review result.
     * Uses cache to avoid re-analyzing identical code.
     */
    public ReviewDTO analyzeAndSave(Long userId, String code, String language) {
        log.info("User {} analyzing {} code ({} chars)", userId, language, code.length());

        // Check cache first
        String codeHash = computeCodeHash(code, language);
        String cacheKey = REVIEW_CACHE_PREFIX + codeHash;

        CachedReview cached = getCachedReview(cacheKey);
        if (cached != null) {
            log.info("Cache hit for code hash={}", codeHash);
            // Still save to history but use cached result
            Review review = buildReview(userId, code, language, cached.reviewResponse);
            reviewRepository.save(review);
            log.info("Cached review saved to history with id={}", review.getId());
            return mapToDTO(review, cached.reviewResponse);
        }

        // Call LLM service
        ReviewResponse reviewResponse = llmService.analyzeCode(code, language);

        // Cache the result
        cacheReviewResult(cacheKey, reviewResponse);

        // Save to database
        Review review = buildReview(userId, code, language, reviewResponse);
        Review saved = reviewRepository.save(review);
        log.info("Review saved with id={}", saved.getId());

        return mapToDTO(saved, reviewResponse);
    }

    /**
     * Analyze multiple files as a codebase.
     */
    public CodebaseReviewResponse analyzeCodebase(Long userId, List<Long> fileIds) {
        log.info("User {} analyzing codebase with {} files", userId, fileIds.size());

        // Load and validate all files
        List<NamedCode> namedCodes = new ArrayList<>();
        List<Long> savedFileIds = new ArrayList<>();
        List<String> savedFilePaths = new ArrayList<>();
        List<String> savedFileLanguages = new ArrayList<>();

        for (Long fileId : fileIds) {
            CodeFile file = codeFileRepository.findById(fileId)
                    .filter(f -> f.getUserId().equals(userId))
                    .orElseThrow(() -> new RuntimeException("File not found with id: " + fileId));
            namedCodes.add(new NamedCode(file.getName(), file.getLanguage(), file.getContent()));
            savedFileIds.add(file.getId());
            savedFilePaths.add(file.getName());
            savedFileLanguages.add(file.getLanguage());
        }

        // Call LLM for multi-file analysis
        MultiFileAnalysisResult multiResult = llmService.analyzeMultipleFiles(namedCodes);
        ReviewResponse reviewResponse = multiResult.getReviewResponse();

        // Count issues
        int[] counts = countIssues(reviewResponse);

        // Save review
        Review review = new Review();
        review.setUserId(userId);
        review.setLanguage("multi");
        review.setSourceType("codebase");
        review.setCodeInput("Codebase review: " + fileIds.size() + " files");
        try {
            review.setReviewResult(objectMapper.writeValueAsString(reviewResponse));
        } catch (Exception e) {
            log.error("Failed to serialize review result", e);
        }
        review.setOverallRating(reviewResponse.getOverallAssessment());
        review.setCriticalCount(counts[0]);
        review.setWarningCount(counts[1]);
        review.setSuggestionCount(counts[2]);

        Review saved = reviewRepository.save(review);
        log.info("Codebase review saved with id={}", saved.getId());

        // Save file associations
        for (int i = 0; i < savedFileIds.size(); i++) {
            ReviewFile reviewFile = ReviewFile.builder()
                    .reviewId(saved.getId())
                    .fileId(savedFileIds.get(i))
                    .filePath(savedFilePaths.get(i))
                    .build();
            reviewFileRepository.save(reviewFile);
        }

        // Build per-file breakdowns from LLM response
        List<CodebaseReviewResponse.FileBreakdown> fileBreakdowns = new ArrayList<>();
        Map<String, MultiFileAnalysisResult.FileLevelSummary> fileReviews = multiResult.getFileReviews();

        for (int i = 0; i < savedFileIds.size(); i++) {
            String fileName = savedFilePaths.get(i);
            CodebaseReviewResponse.FileBreakdown breakdown = new CodebaseReviewResponse.FileBreakdown();
            breakdown.setFileId(savedFileIds.get(i));
            breakdown.setFilePath(fileName);
            breakdown.setLanguage(savedFileLanguages.get(i));

            // Match LLM per-file summary by file name
            MultiFileAnalysisResult.FileLevelSummary fileSummary = fileReviews != null
                    ? fileReviews.get(fileName) : null;
            if (fileSummary != null) {
                breakdown.setAssessment(fileSummary.getAssessment());
                breakdown.setIssueCount(fileSummary.getIssueCount());
            } else {
                // Fallback: if LLM didn't return per-file data, assign default
                breakdown.setAssessment(reviewResponse.getOverallAssessment());
                breakdown.setIssueCount(0);
            }

            fileBreakdowns.add(breakdown);
        }

        // Build response
        CodebaseReviewResponse response = new CodebaseReviewResponse();
        response.setOverallAssessment(reviewResponse.getOverallAssessment());
        response.setIssues(reviewResponse.getIssues());
        response.setSummary(reviewResponse.getSummary());
        response.setTotalFiles(fileIds.size());
        response.setFileBreakdowns(fileBreakdowns);

        return response;
    }

    /**
     * Analyze codebase from provided contents (not saved files).
     */
    public ReviewDTO analyzeCodebaseContents(Long userId, List<CodeFileContent> contents) {
        log.info("User {} analyzing {} codebase contents", userId, contents.size());

        if (contents == null || contents.isEmpty()) {
            throw new RuntimeException("At least one file is required");
        }

        // Convert to NamedCode list
        List<NamedCode> namedCodes = new ArrayList<>();
        StringBuilder combinedCode = new StringBuilder();
        for (CodeFileContent content : contents) {
            namedCodes.add(new NamedCode(content.getName(), content.getLanguage(), content.getContent()));
            combinedCode.append("// File: ").append(content.getName()).append("\n");
            combinedCode.append(content.getContent()).append("\n\n");
        }

        // Call LLM for multi-file analysis
        MultiFileAnalysisResult multiResult = llmService.analyzeMultipleFiles(namedCodes);
        ReviewResponse reviewResponse = multiResult.getReviewResponse();

        // Count issues
        int[] counts = countIssues(reviewResponse);

        // Save review
        Review review = new Review();
        review.setUserId(userId);
        review.setLanguage("multi");
        review.setSourceType("codebase_contents");
        review.setCodeInput(combinedCode.toString().trim());
        try {
            review.setReviewResult(objectMapper.writeValueAsString(reviewResponse));
        } catch (Exception e) {
            log.error("Failed to serialize review result", e);
        }
        review.setOverallRating(reviewResponse.getOverallAssessment());
        review.setCriticalCount(counts[0]);
        review.setWarningCount(counts[1]);
        review.setSuggestionCount(counts[2]);

        Review saved = reviewRepository.save(review);
        log.info("Codebase contents review saved with id={}", saved.getId());

        return mapToDTO(saved, reviewResponse);
    }

    public List<ReviewFile> getReviewFiles(Long reviewId) {
        return reviewFileRepository.findByReviewId(reviewId);
    }

    public Page<ReviewDTO> getReviews(Long userId, int page, int size, String language) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Review> reviews;
        if (language != null && !language.isBlank()) {
            reviews = reviewRepository.findByUserIdAndLanguageOrderByCreatedAtDesc(userId, language, pageable);
        } else {
            reviews = reviewRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }
        return reviews.map(r -> mapToDTO(r, null));
    }

    public ReviewDTO getReview(Long userId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Review not found"));
        return mapToDTO(review, null);
    }

    @Transactional
    public void deleteReview(Long userId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Review not found"));
        reviewFileRepository.deleteByReviewId(reviewId);
        reviewRepository.delete(review);
    }

    // --- Private helpers ---

    /**
     * Compute a hash of the code + language for cache key.
     */
    private String computeCodeHash(String code, String language) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = language + ":" + code;
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 not available", e);
            // Fallback: use hashCode (not cryptographically secure but functional)
            return String.valueOf((language + ":" + code).hashCode());
        }
    }

    /**
     * Try to get a cached review result.
     */
    private CachedReview getCachedReview(String cacheKey) {
        try {
            return cacheService.get(cacheKey)
                    .map(json -> {
                        try {
                            ReviewResponse response = objectMapper.readValue(json, ReviewResponse.class);
                            return new CachedReview(response);
                        } catch (Exception e) {
                            log.warn("Failed to deserialize cached review: {}", e.getMessage());
                            return null;
                        }
                    })
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Cache read failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Cache a review result with 24h TTL.
     */
    private void cacheReviewResult(String cacheKey, ReviewResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            cacheService.set(cacheKey, json);
        } catch (Exception e) {
            log.warn("Failed to cache review result: {}", e.getMessage());
        }
    }

    private Review buildReview(Long userId, String code, String language, ReviewResponse reviewResponse) {
        int[] counts = countIssues(reviewResponse);

        Review review = new Review();
        review.setUserId(userId);
        review.setLanguage(language);
        review.setSourceType("paste");
        review.setCodeInput(code);
        try {
            review.setReviewResult(objectMapper.writeValueAsString(reviewResponse));
        } catch (Exception e) {
            log.error("Failed to serialize review result", e);
        }
        review.setOverallRating(reviewResponse.getOverallAssessment());
        review.setCriticalCount(counts[0]);
        review.setWarningCount(counts[1]);
        review.setSuggestionCount(counts[2]);
        return review;
    }

    private int[] countIssues(ReviewResponse reviewResponse) {
        int criticalCount = 0, warningCount = 0, suggestionCount = 0;
        if (reviewResponse.getIssues() != null) {
            for (ReviewIssue issue : reviewResponse.getIssues()) {
                if (issue.getSeverity() != null) {
                    switch (issue.getSeverity().toLowerCase()) {
                        case "critical", "error" -> criticalCount++;
                        case "warning" -> warningCount++;
                        case "suggestion", "info" -> suggestionCount++;
                    }
                }
            }
        }
        return new int[]{criticalCount, warningCount, suggestionCount};
    }

    private ReviewDTO mapToDTO(Review review, ReviewResponse response) {
        ReviewDTO dto = new ReviewDTO();
        dto.setId(review.getId());
        dto.setUserId(review.getUserId());
        dto.setLanguage(review.getLanguage());
        dto.setSourceType(review.getSourceType());
        dto.setCodeInput(review.getCodeInput());
        dto.setOverallRating(review.getOverallRating());
        dto.setCriticalCount(review.getCriticalCount());
        dto.setWarningCount(review.getWarningCount());
        dto.setSuggestionCount(review.getSuggestionCount());
        dto.setCreatedAt(review.getCreatedAt());
        // Parse reviewResult JSON if needed
        if (response != null) {
            dto.setReviewResult(response);
        } else if (review.getReviewResult() != null) {
            try {
                dto.setReviewResult(objectMapper.readValue(review.getReviewResult(), ReviewResponse.class));
            } catch (Exception e) {
                log.error("Failed to deserialize review result for review {}", review.getId());
            }
        }
        return dto;
    }

    /**
     * Internal record for cached review data.
     */
    private record CachedReview(ReviewResponse reviewResponse) {}
}
