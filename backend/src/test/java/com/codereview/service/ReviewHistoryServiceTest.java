package com.codereview.service;

import com.codereview.dto.ReviewDTO;
import com.codereview.entity.Review;
import com.codereview.repository.ReviewFileRepository;
import com.codereview.repository.ReviewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewHistoryServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewFileRepository reviewFileRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ReviewHistoryService reviewHistoryService;

    private static final Long USER_ID = 1L;
    private static final Long REVIEW_ID = 10L;
    private static final String VALID_CODE = "public class Test { }";
    private static final String VALID_LANGUAGE = "java";

    private Review sampleReview;

    @BeforeEach
    void setUp() {
        sampleReview = new Review();
        sampleReview.setId(REVIEW_ID);
        sampleReview.setUserId(USER_ID);
        sampleReview.setLanguage(VALID_LANGUAGE);
        sampleReview.setSourceType("paste");
        sampleReview.setCodeInput(VALID_CODE);
        sampleReview.setOverallRating("needs_improvement");
        sampleReview.setCriticalCount(1);
        sampleReview.setWarningCount(1);
        sampleReview.setSuggestionCount(1);
        sampleReview.setCreatedAt(LocalDateTime.of(2026, 1, 15, 10, 30));
    }

    // --- getReviewHistory tests ---

    @Test
    void getReviewHistory_ReturnsPaginatedResults() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Review> reviewPage = new PageImpl<>(List.of(sampleReview), pageable, 1);

        when(reviewRepository.findByUserIdOrderByCreatedAtDesc(USER_ID, pageable)).thenReturn(reviewPage);

        Page<ReviewDTO> result = reviewHistoryService.getReviewHistory(USER_ID, 0, 20, null);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        assertEquals(REVIEW_ID, result.getContent().get(0).getId());
    }

    @Test
    void getReviewHistory_WithLanguageFilter_UsesLanguageQuery() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Review> reviewPage = new PageImpl<>(List.of(sampleReview), pageable, 1);

        when(reviewRepository.findByUserIdAndLanguageOrderByCreatedAtDesc(USER_ID, "python", pageable)).thenReturn(reviewPage);

        Page<ReviewDTO> result = reviewHistoryService.getReviewHistory(USER_ID, 0, 10, "python");

        verify(reviewRepository).findByUserIdAndLanguageOrderByCreatedAtDesc(USER_ID, "python", pageable);
        verify(reviewRepository, never()).findByUserIdOrderByCreatedAtDesc(anyLong(), any(Pageable.class));
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @Test
    void getReviewHistory_WithAssessmentFilter_UsesAssessmentQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Review> reviewPage = new PageImpl<>(List.of(sampleReview), pageable, 1);

        when(reviewRepository.findByUserIdAndOverallRatingOrderByCreatedAtDesc(USER_ID, "good", pageable))
                .thenReturn(reviewPage);

        Page<ReviewDTO> result = reviewHistoryService.getReviewHistory(USER_ID, 0, 20, null, "good", null, null);

        verify(reviewRepository).findByUserIdAndOverallRatingOrderByCreatedAtDesc(USER_ID, "good", pageable);
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @Test
    void getReviewHistory_WithDateRange_UsesDateRangeQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 31, 23, 59);
        Page<Review> reviewPage = new PageImpl<>(List.of(sampleReview), pageable, 1);

        when(reviewRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(USER_ID, start, end, pageable))
                .thenReturn(reviewPage);

        Page<ReviewDTO> result = reviewHistoryService.getReviewHistory(USER_ID, 0, 20, null, null, start, end);

        verify(reviewRepository).findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(USER_ID, start, end, pageable);
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @Test
    void getReviewHistory_WithAllFilters_UsesCombinedQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 31, 23, 59);
        Page<Review> reviewPage = new PageImpl<>(List.of(sampleReview), pageable, 1);

        when(reviewRepository.findByUserIdAndLanguageAndCreatedAtBetweenAndOverallRatingOrderByCreatedAtDesc(
                USER_ID, "java", start, end, "good", pageable)).thenReturn(reviewPage);

        Page<ReviewDTO> result = reviewHistoryService.getReviewHistory(USER_ID, 0, 20, "java", "good", start, end);

        verify(reviewRepository).findByUserIdAndLanguageAndCreatedAtBetweenAndOverallRatingOrderByCreatedAtDesc(
                USER_ID, "java", start, end, "good", pageable);
        assertNotNull(result);
    }

    @Test
    void getReviewHistory_BlankLanguageFilter_UsesDefaultQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Review> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(reviewRepository.findByUserIdOrderByCreatedAtDesc(USER_ID, pageable)).thenReturn(emptyPage);

        Page<ReviewDTO> result = reviewHistoryService.getReviewHistory(USER_ID, 0, 20, "  ");

        verify(reviewRepository).findByUserIdOrderByCreatedAtDesc(USER_ID, pageable);
        assertTrue(result.isEmpty());
    }

    @Test
    void getReviewHistory_EmptyResult_ReturnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Review> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(reviewRepository.findByUserIdOrderByCreatedAtDesc(USER_ID, pageable)).thenReturn(emptyPage);

        Page<ReviewDTO> result = reviewHistoryService.getReviewHistory(USER_ID, 0, 20, null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void getReviewHistory_ReviewResultIsParsedFromJSON() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        sampleReview.setReviewResult(objectMapper.writeValueAsString(
                new com.codereview.dto.ReviewResponse("needs_improvement", List.of(), "summary")));
        Page<Review> reviewPage = new PageImpl<>(List.of(sampleReview), pageable, 1);

        when(reviewRepository.findByUserIdOrderByCreatedAtDesc(USER_ID, pageable)).thenReturn(reviewPage);

        Page<ReviewDTO> result = reviewHistoryService.getReviewHistory(USER_ID, 0, 20, null);

        ReviewDTO dto = result.getContent().get(0);
        assertNotNull(dto.getReviewResult());
        assertEquals("needs_improvement", dto.getReviewResult().getOverallAssessment());
    }

    // --- getReviewDetail tests ---

    @Test
    void getReviewDetail_ReturnsReviewWhenOwnedByUser() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(sampleReview));

        ReviewDTO result = reviewHistoryService.getReviewDetail(USER_ID, REVIEW_ID);

        assertNotNull(result);
        assertEquals(REVIEW_ID, result.getId());
        assertEquals(VALID_LANGUAGE, result.getLanguage());
        assertEquals(VALID_CODE, result.getCodeInput());
    }

    @Test
    void getReviewDetail_ThrowsExceptionWhenNotFound() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> reviewHistoryService.getReviewDetail(USER_ID, REVIEW_ID));

        assertEquals("Review not found", exception.getMessage());
    }

    @Test
    void getReviewDetail_ThrowsExceptionWhenNotOwnedByUser() {
        Review otherUserReview = new Review();
        otherUserReview.setId(REVIEW_ID);
        otherUserReview.setUserId(999L);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(otherUserReview));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> reviewHistoryService.getReviewDetail(USER_ID, REVIEW_ID));

        assertEquals("Review not found", exception.getMessage());
    }

    @Test
    void getReviewDetail_ParsesReviewResultFromStoredJSON() throws Exception {
        sampleReview.setReviewResult(objectMapper.writeValueAsString(
                new com.codereview.dto.ReviewResponse("good", List.of(), "Clean code")));
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(sampleReview));

        ReviewDTO result = reviewHistoryService.getReviewDetail(USER_ID, REVIEW_ID);

        assertNotNull(result.getReviewResult());
        assertEquals("good", result.getReviewResult().getOverallAssessment());
    }

    // --- deleteReview tests ---

    @Test
    void deleteReview_ExistingReview_DeletesSuccessfully() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(sampleReview));

        reviewHistoryService.deleteReview(USER_ID, REVIEW_ID);

        verify(reviewFileRepository).deleteByReviewId(REVIEW_ID);
        verify(reviewRepository).delete(sampleReview);
    }

    @Test
    void deleteReview_NotFound_ThrowsException() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> reviewHistoryService.deleteReview(USER_ID, REVIEW_ID));

        verify(reviewRepository, never()).delete(any());
    }

    @Test
    void deleteReview_OwnedByDifferentUser_ThrowsException() {
        Review otherUserReview = new Review();
        otherUserReview.setId(REVIEW_ID);
        otherUserReview.setUserId(999L);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(otherUserReview));

        assertThrows(RuntimeException.class,
                () -> reviewHistoryService.deleteReview(USER_ID, REVIEW_ID));

        verify(reviewRepository, never()).delete(any());
    }

    // --- mapToDTO tests ---

    @Test
    void mapToDTO_ConvertsEntityToDTO() {
        ReviewDTO dto = reviewHistoryService.mapToDTO(sampleReview);

        assertNotNull(dto);
        assertEquals(REVIEW_ID, dto.getId());
        assertEquals(VALID_LANGUAGE, dto.getLanguage());
        assertEquals("paste", dto.getSourceType());
        assertEquals(VALID_CODE, dto.getCodeInput());
        assertEquals("needs_improvement", dto.getOverallRating());
        assertEquals(1, dto.getCriticalCount());
        assertEquals(1, dto.getWarningCount());
        assertEquals(1, dto.getSuggestionCount());
        assertNotNull(dto.getCreatedAt());
    }

    @Test
    void mapToDTO_WithNullReviewResult_ReturnsDTOWithoutResult() {
        sampleReview.setReviewResult(null);

        ReviewDTO dto = reviewHistoryService.mapToDTO(sampleReview);

        assertNotNull(dto);
        assertNull(dto.getReviewResult());
    }

    @Test
    void mapToDTO_WithInvalidReviewResultJson_HandlesGracefully() {
        sampleReview.setReviewResult("not valid json {");

        ReviewDTO dto = reviewHistoryService.mapToDTO(sampleReview);

        assertNotNull(dto);
        assertNull(dto.getReviewResult());
    }
}
