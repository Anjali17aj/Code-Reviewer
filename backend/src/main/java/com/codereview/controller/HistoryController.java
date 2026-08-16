package com.codereview.controller;

import com.codereview.dto.ReviewDTO;
import com.codereview.service.ReviewHistoryService;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final ReviewHistoryService reviewHistoryService;

    /**
     * GET /api/history
     * Returns paginated review history for the authenticated user.
     * Includes both paste and codebase reviews.
     *
     * @param page       zero-based page index (default 0)
     * @param size       page size (default 20)
     * @param language   optional language filter
     * @param assessment optional assessment filter (good, needs_improvement, poor)
     * @param startDate  optional start date (yyyy-MM-dd or yyyy-MM-ddTHH:mm:ss)
     * @param endDate    optional end date (yyyy-MM-dd or yyyy-MM-ddTHH:mm:ss)
     * @return paginated ReviewDTOs
     */
    @GetMapping
    public ResponseEntity<Page<ReviewDTO>> getReviewHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") @Max(100) int size,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String assessment,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long userId = extractUserId(userDetails);
        LocalDateTime startDateTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime = endDate != null ? endDate.atTime(23, 59, 59) : null;
        return ResponseEntity.ok(reviewHistoryService.getReviewHistory(
                userId, page, size, language, assessment, startDateTime, endDateTime));
    }

    /**
     * GET /api/history/{id}
     * Returns a single review detail by ID for the authenticated user.
     *
     * @param id the review ID
     * @return ReviewDTO
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReviewDTO> getReviewDetail(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        Long userId = extractUserId(userDetails);
        return ResponseEntity.ok(reviewHistoryService.getReviewDetail(userId, id));
    }

    /**
     * DELETE /api/history/{id}
     * Delete a review by ID for the authenticated user.
     *
     * @param id the review ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReview(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        Long userId = extractUserId(userDetails);
        reviewHistoryService.deleteReview(userId, id);
        return ResponseEntity.noContent().build();
    }

    private Long extractUserId(UserDetails userDetails) {
        if (userDetails instanceof com.codereview.service.JwtUserDetails jwtUser) {
            return jwtUser.getUserId();
        }
        throw new RuntimeException("Invalid authentication");
    }
}
