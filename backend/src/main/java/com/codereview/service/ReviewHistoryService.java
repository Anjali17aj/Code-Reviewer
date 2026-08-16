package com.codereview.service;

import com.codereview.dto.ReviewDTO;
import com.codereview.dto.ReviewResponse;
import com.codereview.entity.Review;
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

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewHistoryService {

    private final ReviewRepository reviewRepository;
    private final ReviewFileRepository reviewFileRepository;
    private final ObjectMapper objectMapper;

    /**
     * Get paginated review history for a user with optional filters.
     *
     * @param userId          the user's ID
     * @param page            zero-based page index
     * @param size            page size
     * @param language        optional language filter (null or blank for all)
     * @param assessment      optional assessment filter (null or blank for all)
     * @param startDate       optional start date for range filter
     * @param endDate         optional end date for range filter
     * @return paginated ReviewDTOs
     */
    public Page<ReviewDTO> getReviewHistory(Long userId, int page, int size,
                                             String language, String assessment,
                                             LocalDateTime startDate, LocalDateTime endDate) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Review> reviews;

        boolean hasLanguage = language != null && !language.isBlank();
        boolean hasAssessment = assessment != null && !assessment.isBlank();
        boolean hasDateRange = startDate != null && endDate != null;

        if (hasLanguage && hasDateRange && hasAssessment) {
            reviews = reviewRepository.findByUserIdAndLanguageAndCreatedAtBetweenAndOverallRatingOrderByCreatedAtDesc(
                    userId, language, startDate, endDate, assessment, pageable);
        } else if (hasLanguage && hasDateRange) {
            reviews = reviewRepository.findByUserIdAndLanguageAndCreatedAtBetweenOrderByCreatedAtDesc(
                    userId, language, startDate, endDate, pageable);
        } else if (hasLanguage && hasAssessment) {
            reviews = reviewRepository.findByUserIdAndLanguageAndOverallRatingOrderByCreatedAtDesc(
                    userId, language, assessment, pageable);
        } else if (hasDateRange && hasAssessment) {
            reviews = reviewRepository.findByUserIdAndCreatedAtBetweenAndOverallRatingOrderByCreatedAtDesc(
                    userId, startDate, endDate, assessment, pageable);
        } else if (hasLanguage) {
            reviews = reviewRepository.findByUserIdAndLanguageOrderByCreatedAtDesc(userId, language, pageable);
        } else if (hasAssessment) {
            reviews = reviewRepository.findByUserIdAndOverallRatingOrderByCreatedAtDesc(userId, assessment, pageable);
        } else if (hasDateRange) {
            reviews = reviewRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                    userId, startDate, endDate, pageable);
        } else {
            reviews = reviewRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        log.debug("Retrieved review history for user={}, page={}, size={}, total={}",
                userId, page, size, reviews.getTotalElements());

        return reviews.map(this::mapToDTO);
    }

    /**
     * Legacy method for backward compatibility.
     */
    public Page<ReviewDTO> getReviewHistory(Long userId, int page, int size, String language) {
        return getReviewHistory(userId, page, size, language, null, null, null);
    }

    /**
     * Get a single review detail by ID, ensuring the review belongs to the specified user.
     *
     * @param userId   the user's ID
     * @param reviewId the review's ID
     * @return ReviewDTO
     * @throws RuntimeException if review not found or not owned by user
     */
    public ReviewDTO getReviewDetail(Long userId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Review not found"));

        log.debug("Retrieved review detail id={} for user={}", reviewId, userId);

        return mapToDTO(review);
    }

    /**
     * Delete a review by ID, ensuring it belongs to the specified user.
     *
     * @param userId   the user's ID
     * @param reviewId the review's ID
     * @throws RuntimeException if review not found or not owned by user
     */
    @Transactional
    public void deleteReview(Long userId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Review not found"));

        reviewFileRepository.deleteByReviewId(reviewId);
        reviewRepository.delete(review);
        log.info("Deleted review id={} for user={}", reviewId, userId);
    }

    /**
     * Convert a Review entity to a ReviewDTO.
     * Parses the stored JSON reviewResult into a ReviewResponse object.
     *
     * @param review the Review entity
     * @return populated ReviewDTO
     */
    ReviewDTO mapToDTO(Review review) {
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

        if (review.getReviewResult() != null) {
            try {
                dto.setReviewResult(objectMapper.readValue(review.getReviewResult(), ReviewResponse.class));
            } catch (Exception e) {
                log.error("Failed to deserialize review result for review {}", review.getId(), e);
            }
        }

        return dto;
    }
}
