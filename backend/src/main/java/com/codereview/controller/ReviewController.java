package com.codereview.controller;

import com.codereview.dto.*;
import com.codereview.entity.ReviewFile;
import com.codereview.service.RateLimitService;
import com.codereview.service.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final RateLimitService rateLimitService;

    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ReviewRequest request) {
        Long userId = extractUserId(userDetails);

        // Check rate limit before processing
        if (!rateLimitService.isAllowed(userId)) {
            long remaining = rateLimitService.getRemainingRequests(userId);
            long retryAfter = rateLimitService.getTimeUntilReset(userId);

            Map<String, Object> errorBody = Map.of(
                    "timestamp", java.time.LocalDateTime.now().toString(),
                    "message", "Rate limit exceeded. Please try again later.",
                    "status", HttpStatus.TOO_MANY_REQUESTS.value(),
                    "remainingRequests", remaining,
                    "retryAfterSeconds", retryAfter
            );

            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(Math.max(1, retryAfter)))
                    .header("X-RateLimit-Limit", String.valueOf(rateLimitService.getMaxReviewsPerDay()))
                    .header("X-RateLimit-Remaining", "0")
                    .body(errorBody);
        }

        ReviewDTO result = reviewService.analyzeAndSave(userId, request.getCode(), request.getLanguage());

        // Add rate limit headers to successful response
        long remaining = rateLimitService.getRemainingRequests(userId);
        return ResponseEntity.ok()
                .header("X-RateLimit-Limit", String.valueOf(rateLimitService.getMaxReviewsPerDay()))
                .header("X-RateLimit-Remaining", String.valueOf(remaining))
                .body(result);
    }

    @PostMapping("/analyze-codebase")
    public ResponseEntity<?> analyzeCodebase(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CodebaseReviewRequest request) {
        Long userId = extractUserId(userDetails);

        if (!rateLimitService.isAllowed(userId)) {
            long remaining = rateLimitService.getRemainingRequests(userId);
            long retryAfter = rateLimitService.getTimeUntilReset(userId);

            Map<String, Object> errorBody = Map.of(
                    "timestamp", java.time.LocalDateTime.now().toString(),
                    "message", "Rate limit exceeded. Please try again later.",
                    "status", HttpStatus.TOO_MANY_REQUESTS.value(),
                    "remainingRequests", remaining,
                    "retryAfterSeconds", retryAfter
            );

            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(Math.max(1, retryAfter)))
                    .header("X-RateLimit-Limit", String.valueOf(rateLimitService.getMaxReviewsPerDay()))
                    .header("X-RateLimit-Remaining", "0")
                    .body(errorBody);
        }

        CodebaseReviewResponse result = reviewService.analyzeCodebase(userId, request.getFileIds());
        long remaining = rateLimitService.getRemainingRequests(userId);
        return ResponseEntity.ok()
                .header("X-RateLimit-Limit", String.valueOf(rateLimitService.getMaxReviewsPerDay()))
                .header("X-RateLimit-Remaining", String.valueOf(remaining))
                .body(result);
    }

    @PostMapping("/analyze-files")
    public ResponseEntity<?> analyzeFiles(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CodebaseFilesRequest request) {
        Long userId = extractUserId(userDetails);

        if (!rateLimitService.isAllowed(userId)) {
            long remaining = rateLimitService.getRemainingRequests(userId);
            long retryAfter = rateLimitService.getTimeUntilReset(userId);

            Map<String, Object> errorBody = Map.of(
                    "timestamp", java.time.LocalDateTime.now().toString(),
                    "message", "Rate limit exceeded. Please try again later.",
                    "status", HttpStatus.TOO_MANY_REQUESTS.value(),
                    "remainingRequests", remaining,
                    "retryAfterSeconds", retryAfter
            );

            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(Math.max(1, retryAfter)))
                    .header("X-RateLimit-Limit", String.valueOf(rateLimitService.getMaxReviewsPerDay()))
                    .header("X-RateLimit-Remaining", "0")
                    .body(errorBody);
        }

        ReviewDTO result = reviewService.analyzeCodebaseContents(userId, request.getFiles());
        long remaining = rateLimitService.getRemainingRequests(userId);
        return ResponseEntity.ok()
                .header("X-RateLimit-Limit", String.valueOf(rateLimitService.getMaxReviewsPerDay()))
                .header("X-RateLimit-Remaining", String.valueOf(remaining))
                .body(result);
    }

    @GetMapping("/{id}/files")
    public ResponseEntity<List<ReviewFile>> getReviewFiles(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        Long userId = extractUserId(userDetails);
        // Verify the review belongs to this user
        reviewService.getReview(userId, id);
        List<ReviewFile> files = reviewService.getReviewFiles(id);
        return ResponseEntity.ok(files);
    }

    @GetMapping
    public ResponseEntity<List<ReviewDTO>> getReviews(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") @Max(100) int size,
            @RequestParam(required = false) String language) {
        Long userId = extractUserId(userDetails);
        Page<ReviewDTO> reviews = reviewService.getReviews(userId, page, size, language);
        return ResponseEntity.ok(reviews.getContent());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReviewDTO> getReview(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        Long userId = extractUserId(userDetails);
        return ResponseEntity.ok(reviewService.getReview(userId, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReview(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        Long userId = extractUserId(userDetails);
        reviewService.deleteReview(userId, id);
        return ResponseEntity.noContent().build();
    }

    private Long extractUserId(UserDetails userDetails) {
        if (userDetails instanceof com.codereview.service.JwtUserDetails jwtUser) {
            return jwtUser.getUserId();
        }
        throw new RuntimeException("Invalid authentication");
    }
}
