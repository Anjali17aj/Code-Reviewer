package com.codereview.service;

import com.codereview.dto.CodeFileContent;
import com.codereview.dto.CodebaseReviewResponse;
import com.codereview.dto.MultiFileAnalysisResult;
import com.codereview.dto.NamedCode;
import com.codereview.dto.ReviewDTO;
import com.codereview.dto.ReviewIssue;
import com.codereview.dto.ReviewResponse;
import com.codereview.entity.CodeFile;
import com.codereview.entity.Review;
import com.codereview.entity.ReviewFile;
import com.codereview.repository.CodeFileRepository;
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
class ReviewServiceTest {

    @Mock
    private LLMService llmService;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewFileRepository reviewFileRepository;

    @Mock
    private CodeFileRepository codeFileRepository;

    @Mock
    private CacheService cacheService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ReviewService reviewService;

    private static final Long USER_ID = 1L;
    private static final Long REVIEW_ID = 10L;
    private static final String VALID_CODE = "public class Test { }";
    private static final String VALID_LANGUAGE = "java";

    private ReviewResponse sampleReviewResponse;

    @BeforeEach
    void setUp() {
        ReviewIssue criticalIssue = new ReviewIssue(1, "error", "security", "SQL injection", "Use parameterized queries");
        ReviewIssue warningIssue = new ReviewIssue(5, "warning", "performance", "N+1 query", "Use JOIN fetch");
        ReviewIssue suggestionIssue = new ReviewIssue(10, "info", "style", "Missing Javadoc", "Add method documentation");

        sampleReviewResponse = new ReviewResponse();
        sampleReviewResponse.setOverallAssessment("needs_improvement");
        sampleReviewResponse.setIssues(List.of(criticalIssue, warningIssue, suggestionIssue));
        sampleReviewResponse.setSummary("Code has security and performance issues.");
    }

    // --- analyzeAndSave tests ---

    @Test
    void analyzeAndSave_CallsLLMServiceWithCorrectParams() {
        when(llmService.analyzeCode(VALID_CODE, VALID_LANGUAGE)).thenReturn(sampleReviewResponse);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(REVIEW_ID);
            return r;
        });

        reviewService.analyzeAndSave(USER_ID, VALID_CODE, VALID_LANGUAGE);

        verify(llmService).analyzeCode(VALID_CODE, VALID_LANGUAGE);
    }

    @Test
    void analyzeAndSave_SavesReviewToRepository() {
        when(llmService.analyzeCode(VALID_CODE, VALID_LANGUAGE)).thenReturn(sampleReviewResponse);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(REVIEW_ID);
            return r;
        });

        reviewService.analyzeAndSave(USER_ID, VALID_CODE, VALID_LANGUAGE);

        verify(reviewRepository).save(argThat(review ->
                review.getUserId().equals(USER_ID) &&
                review.getLanguage().equals(VALID_LANGUAGE) &&
                review.getSourceType().equals("paste") &&
                review.getCodeInput().equals(VALID_CODE) &&
                review.getOverallRating().equals("needs_improvement") &&
                review.getCriticalCount() == 1 &&
                review.getWarningCount() == 1 &&
                review.getSuggestionCount() == 1
        ));
    }

    @Test
    void analyzeAndSave_CountsSeverityCorrectly() {
        // All critical issues
        ReviewIssue issue1 = new ReviewIssue(1, "error", "security", "msg1", "fix1");
        ReviewIssue issue2 = new ReviewIssue(2, "error", "bug", "msg2", "fix2");
        ReviewResponse allCritical = new ReviewResponse("poor", List.of(issue1, issue2), "Poor code");

        when(llmService.analyzeCode(VALID_CODE, VALID_LANGUAGE)).thenReturn(allCritical);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(REVIEW_ID);
            return r;
        });

        ReviewDTO result = reviewService.analyzeAndSave(USER_ID, VALID_CODE, VALID_LANGUAGE);

        verify(reviewRepository).save(argThat(review ->
                review.getCriticalCount() == 2 &&
                review.getWarningCount() == 0 &&
                review.getSuggestionCount() == 0
        ));
        assertEquals(2, result.getCriticalCount());
    }

    @Test
    void analyzeAndSave_CountsCriticalSeverityKeyword() {
        // "critical" severity should map to critical count
        ReviewIssue criticalIssue = new ReviewIssue(1, "critical", "security", "msg", "fix");
        ReviewResponse response = new ReviewResponse("poor", List.of(criticalIssue), "Poor code");

        when(llmService.analyzeCode(VALID_CODE, VALID_LANGUAGE)).thenReturn(response);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(REVIEW_ID);
            return r;
        });

        ReviewDTO result = reviewService.analyzeAndSave(USER_ID, VALID_CODE, VALID_LANGUAGE);

        assertEquals(1, result.getCriticalCount());
    }

    @Test
    void analyzeAndSave_ReturnsReviewDTOWithId() {
        when(llmService.analyzeCode(VALID_CODE, VALID_LANGUAGE)).thenReturn(sampleReviewResponse);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(REVIEW_ID);
            r.setCreatedAt(LocalDateTime.of(2026, 1, 15, 10, 30));
            return r;
        });

        ReviewDTO result = reviewService.analyzeAndSave(USER_ID, VALID_CODE, VALID_LANGUAGE);

        assertNotNull(result);
        assertEquals(REVIEW_ID, result.getId());
        assertEquals(VALID_LANGUAGE, result.getLanguage());
        assertEquals("paste", result.getSourceType());
        assertEquals(VALID_CODE, result.getCodeInput());
        assertEquals("needs_improvement", result.getOverallRating());
        assertEquals(1, result.getCriticalCount());
        assertEquals(1, result.getWarningCount());
        assertEquals(1, result.getSuggestionCount());
        assertNotNull(result.getReviewResult());
        assertEquals(sampleReviewResponse, result.getReviewResult());
    }

    @Test
    void analyzeAndSave_EmptyIssues_AllCountsZero() {
        ReviewResponse noIssues = new ReviewResponse("good", List.of(), "Clean code");

        when(llmService.analyzeCode(VALID_CODE, VALID_LANGUAGE)).thenReturn(noIssues);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(REVIEW_ID);
            return r;
        });

        ReviewDTO result = reviewService.analyzeAndSave(USER_ID, VALID_CODE, VALID_LANGUAGE);

        assertEquals(0, result.getCriticalCount());
        assertEquals(0, result.getWarningCount());
        assertEquals(0, result.getSuggestionCount());
        assertEquals("good", result.getOverallRating());
    }

    // --- Cache integration tests ---

    @Test
    void analyzeAndSave_CacheHit_SkipsLLMAndSavesToHistory() throws Exception {
        String cachedJson = objectMapper.writeValueAsString(sampleReviewResponse);
        when(cacheService.get(anyString())).thenReturn(Optional.of(cachedJson));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(REVIEW_ID);
            r.setCreatedAt(LocalDateTime.of(2026, 1, 15, 10, 30));
            return r;
        });

        ReviewDTO result = reviewService.analyzeAndSave(USER_ID, VALID_CODE, VALID_LANGUAGE);

        // Should NOT call LLM on cache hit
        verify(llmService, never()).analyzeCode(anyString(), anyString());
        // Should still save to review history
        verify(reviewRepository).save(any(Review.class));
        assertNotNull(result);
        assertEquals("needs_improvement", result.getReviewResult().getOverallAssessment());
    }

    @Test
    void analyzeAndSave_CacheMiss_CallsLLMAndCachesResult() throws Exception {
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(llmService.analyzeCode(VALID_CODE, VALID_LANGUAGE)).thenReturn(sampleReviewResponse);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(REVIEW_ID);
            r.setCreatedAt(LocalDateTime.of(2026, 1, 15, 10, 30));
            return r;
        });

        ReviewDTO result = reviewService.analyzeAndSave(USER_ID, VALID_CODE, VALID_LANGUAGE);

        // Should call LLM on cache miss
        verify(llmService).analyzeCode(VALID_CODE, VALID_LANGUAGE);
        // Should cache the result
        verify(cacheService).set(anyString(), anyString());
        // Should save to review history
        verify(reviewRepository).save(any(Review.class));
        assertNotNull(result);
    }

    @Test
    void analyzeAndSave_CacheReadError_StillCallsLLM() {
        when(cacheService.get(anyString())).thenThrow(new RuntimeException("Redis down"));
        when(llmService.analyzeCode(VALID_CODE, VALID_LANGUAGE)).thenReturn(sampleReviewResponse);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(REVIEW_ID);
            return r;
        });

        ReviewDTO result = reviewService.analyzeAndSave(USER_ID, VALID_CODE, VALID_LANGUAGE);

        // Should still call LLM when cache fails
        verify(llmService).analyzeCode(VALID_CODE, VALID_LANGUAGE);
        assertNotNull(result);
    }

    // --- getReviews tests ---

    @Test
    void getReviews_ReturnsPaginatedResults() {
        Pageable pageable = PageRequest.of(0, 20);
        Review review = buildSampleReview();
        Page<Review> reviewPage = new PageImpl<>(List.of(review), pageable, 1);

        when(reviewRepository.findByUserIdOrderByCreatedAtDesc(USER_ID, pageable)).thenReturn(reviewPage);

        Page<ReviewDTO> result = reviewService.getReviews(USER_ID, 0, 20, null);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        assertEquals(REVIEW_ID, result.getContent().get(0).getId());
    }

    @Test
    void getReviews_WithLanguageFilter_UsesLanguageQuery() {
        Pageable pageable = PageRequest.of(0, 10);
        Review review = buildSampleReview();
        Page<Review> reviewPage = new PageImpl<>(List.of(review), pageable, 1);

        when(reviewRepository.findByUserIdAndLanguageOrderByCreatedAtDesc(USER_ID, "python", pageable)).thenReturn(reviewPage);

        Page<ReviewDTO> result = reviewService.getReviews(USER_ID, 0, 10, "python");

        verify(reviewRepository).findByUserIdAndLanguageOrderByCreatedAtDesc(USER_ID, "python", pageable);
        verify(reviewRepository, never()).findByUserIdOrderByCreatedAtDesc(anyLong(), any(Pageable.class));
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @Test
    void getReviews_BlankLanguageFilter_UsesDefaultQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Review> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(reviewRepository.findByUserIdOrderByCreatedAtDesc(USER_ID, pageable)).thenReturn(emptyPage);

        Page<ReviewDTO> result = reviewService.getReviews(USER_ID, 0, 20, "  ");

        verify(reviewRepository).findByUserIdOrderByCreatedAtDesc(USER_ID, pageable);
        assertTrue(result.isEmpty());
    }

    @Test
    void getReviews_EmptyResult_ReturnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Review> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(reviewRepository.findByUserIdOrderByCreatedAtDesc(USER_ID, pageable)).thenReturn(emptyPage);

        Page<ReviewDTO> result = reviewService.getReviews(USER_ID, 0, 20, null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void getReviews_ReviewResultIsParsedFromJSON() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        Review review = buildSampleReview();
        // reviewResult is already stored as JSON string
        review.setReviewResult(objectMapper.writeValueAsString(sampleReviewResponse));
        Page<Review> reviewPage = new PageImpl<>(List.of(review), pageable, 1);

        when(reviewRepository.findByUserIdOrderByCreatedAtDesc(USER_ID, pageable)).thenReturn(reviewPage);

        Page<ReviewDTO> result = reviewService.getReviews(USER_ID, 0, 20, null);

        ReviewDTO dto = result.getContent().get(0);
        assertNotNull(dto.getReviewResult());
        assertEquals("needs_improvement", dto.getReviewResult().getOverallAssessment());
        assertEquals(3, dto.getReviewResult().getIssues().size());
    }

    // --- getReview tests ---

    @Test
    void getReview_ReturnsReviewWhenOwnedByUser() {
        Review review = buildSampleReview();
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));

        ReviewDTO result = reviewService.getReview(USER_ID, REVIEW_ID);

        assertNotNull(result);
        assertEquals(REVIEW_ID, result.getId());
        assertEquals(VALID_LANGUAGE, result.getLanguage());
    }

    @Test
    void getReview_ThrowsExceptionWhenNotFound() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> reviewService.getReview(USER_ID, REVIEW_ID));

        assertEquals("Review not found", exception.getMessage());
    }

    @Test
    void getReview_ThrowsExceptionWhenNotOwnedByUser() {
        Review review = buildSampleReview();
        review.setUserId(999L); // Different user
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> reviewService.getReview(USER_ID, REVIEW_ID));

        assertEquals("Review not found", exception.getMessage());
    }

    @Test
    void getReview_ParsesReviewResultFromStoredJSON() throws Exception {
        Review review = buildSampleReview();
        review.setReviewResult(objectMapper.writeValueAsString(sampleReviewResponse));
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));

        ReviewDTO result = reviewService.getReview(USER_ID, REVIEW_ID);

        assertNotNull(result.getReviewResult());
        assertEquals("needs_improvement", result.getReviewResult().getOverallAssessment());
    }

    // --- deleteReview tests ---

    @Test
    void deleteReview_DeletesWhenOwnedByUser() {
        Review review = buildSampleReview();
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));

        reviewService.deleteReview(USER_ID, REVIEW_ID);

        verify(reviewFileRepository).deleteByReviewId(REVIEW_ID);
        verify(reviewRepository).delete(review);
    }

    @Test
    void deleteReview_ThrowsExceptionWhenNotFound() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> reviewService.deleteReview(USER_ID, REVIEW_ID));

        verify(reviewRepository, never()).delete(any());
    }

    @Test
    void deleteReview_ThrowsExceptionWhenNotOwnedByUser() {
        Review review = buildSampleReview();
        review.setUserId(999L);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));

        assertThrows(RuntimeException.class,
                () -> reviewService.deleteReview(USER_ID, REVIEW_ID));

        verify(reviewRepository, never()).delete(any());
    }

    // --- analyzeCodebase tests ---

    @Test
    void analyzeCodebase_LoadsFilesAndCallsLLM() {
        Long fileId1 = 1L, fileId2 = 2L;
        CodeFile file1 = CodeFile.builder()
                .id(fileId1).userId(USER_ID).name("Foo.java").language("java").content("class Foo { }").build();
        CodeFile file2 = CodeFile.builder()
                .id(fileId2).userId(USER_ID).name("Bar.java").language("java").content("class Bar { }").build();

        when(codeFileRepository.findById(fileId1)).thenReturn(Optional.of(file1));
        when(codeFileRepository.findById(fileId2)).thenReturn(Optional.of(file2));
        when(llmService.analyzeMultipleFiles(anyList())).thenReturn(buildMultiFileResult(sampleReviewResponse));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(REVIEW_ID);
            return r;
        });

        CodebaseReviewResponse result = reviewService.analyzeCodebase(USER_ID, List.of(fileId1, fileId2));

        assertNotNull(result);
        verify(llmService).analyzeMultipleFiles(argThat(files -> files.size() == 2));
    }

    @Test
    void analyzeCodebase_SavesReviewWithSourceTypeCodebase() {
        Long fileId1 = 1L;
        CodeFile file1 = CodeFile.builder()
                .id(fileId1).userId(USER_ID).name("Foo.java").language("java").content("class Foo { }").build();

        when(codeFileRepository.findById(fileId1)).thenReturn(Optional.of(file1));
        when(llmService.analyzeMultipleFiles(anyList())).thenReturn(buildMultiFileResult(sampleReviewResponse));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(REVIEW_ID);
            return r;
        });

        reviewService.analyzeCodebase(USER_ID, List.of(fileId1));

        verify(reviewRepository).save(argThat(review ->
                review.getSourceType().equals("codebase") &&
                review.getUserId().equals(USER_ID)
        ));
    }

    @Test
    void analyzeCodebase_SavesReviewFileAssociations() {
        Long fileId1 = 1L, fileId2 = 2L;
        CodeFile file1 = CodeFile.builder()
                .id(fileId1).userId(USER_ID).name("Foo.java").language("java").content("class Foo { }").build();
        CodeFile file2 = CodeFile.builder()
                .id(fileId2).userId(USER_ID).name("Bar.java").language("java").content("class Bar { }").build();

        when(codeFileRepository.findById(fileId1)).thenReturn(Optional.of(file1));
        when(codeFileRepository.findById(fileId2)).thenReturn(Optional.of(file2));
        when(llmService.analyzeMultipleFiles(anyList())).thenReturn(buildMultiFileResult(sampleReviewResponse));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(REVIEW_ID);
            return r;
        });

        reviewService.analyzeCodebase(USER_ID, List.of(fileId1, fileId2));

        verify(reviewFileRepository, times(2)).save(any(ReviewFile.class));
    }

    @Test
    void analyzeCodebase_ThrowsWhenFileNotFound() {
        when(codeFileRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> reviewService.analyzeCodebase(USER_ID, List.of(99L)));
        assertEquals("File not found with id: 99", ex.getMessage());
    }

    @Test
    void analyzeCodebase_ThrowsWhenFileNotOwnedByUser() {
        CodeFile file = CodeFile.builder()
                .id(1L).userId(999L).name("Foo.java").language("java").content("class Foo { }").build();
        when(codeFileRepository.findById(1L)).thenReturn(Optional.of(file));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> reviewService.analyzeCodebase(USER_ID, List.of(1L)));
        assertEquals("File not found with id: 1", ex.getMessage());
    }

    @Test
    void analyzeCodebase_ReturnsCodebaseReviewResponse() {
        Long fileId1 = 1L;
        CodeFile file1 = CodeFile.builder()
                .id(fileId1).userId(USER_ID).name("Foo.java").language("java").content("class Foo { }").build();

        ReviewResponse reviewResponse = new ReviewResponse();
        reviewResponse.setOverallAssessment("good");
        reviewResponse.setIssues(List.of());
        reviewResponse.setSummary("All files look good.");

        when(codeFileRepository.findById(fileId1)).thenReturn(Optional.of(file1));
        when(llmService.analyzeMultipleFiles(anyList())).thenReturn(buildMultiFileResult(reviewResponse));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(REVIEW_ID);
            return r;
        });

        CodebaseReviewResponse result = reviewService.analyzeCodebase(USER_ID, List.of(fileId1));

        assertNotNull(result);
        assertEquals("good", result.getOverallAssessment());
        assertEquals(1, result.getTotalFiles());
    }

    // --- analyzeCodebaseContents tests ---

    @Test
    void analyzeCodebaseContents_CallsLLMWithProvidedContents() {
        List<CodeFileContent> contents = List.of(
                new CodeFileContent("Foo.java", "java", "class Foo { }"),
                new CodeFileContent("Bar.java", "python", "class Bar: pass")
        );

        when(llmService.analyzeMultipleFiles(anyList())).thenReturn(buildMultiFileResult(sampleReviewResponse));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(REVIEW_ID);
            return r;
        });

        ReviewDTO result = reviewService.analyzeCodebaseContents(USER_ID, contents);

        assertNotNull(result);
        verify(llmService).analyzeMultipleFiles(argThat(files -> files.size() == 2));
    }

    @Test
    void analyzeCodebaseContents_SavesReviewWithSourceTypeCodebaseContents() {
        List<CodeFileContent> contents = List.of(
                new CodeFileContent("Foo.java", "java", "class Foo { }")
        );

        when(llmService.analyzeMultipleFiles(anyList())).thenReturn(buildMultiFileResult(sampleReviewResponse));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(REVIEW_ID);
            return r;
        });

        reviewService.analyzeCodebaseContents(USER_ID, contents);

        verify(reviewRepository).save(argThat(review ->
                review.getSourceType().equals("codebase_contents")
        ));
    }

    @Test
    void analyzeCodebaseContents_BuildsNamedCodeFromContents() {
        List<CodeFileContent> contents = List.of(
                new CodeFileContent("src/Foo.java", "java", "class Foo { }")
        );

        when(llmService.analyzeMultipleFiles(anyList())).thenReturn(buildMultiFileResult(sampleReviewResponse));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(REVIEW_ID);
            return r;
        });

        reviewService.analyzeCodebaseContents(USER_ID, contents);

        verify(llmService).analyzeMultipleFiles(argThat(files -> {
            if (files.size() != 1) return false;
            NamedCode nc = files.get(0);
            return "src/Foo.java".equals(nc.getFileName())
                    && "java".equals(nc.getLanguage())
                    && "class Foo { }".equals(nc.getCode());
        }));
    }

    @Test
    void analyzeCodebaseContents_EmptyContents_ThrowsException() {
        assertThrows(RuntimeException.class,
                () -> reviewService.analyzeCodebaseContents(USER_ID, List.of()));
    }

    // --- getReviewFiles tests ---

    @Test
    void getReviewFiles_ReturnsFilesForReview() {
        ReviewFile rf1 = ReviewFile.builder().id(1L).reviewId(REVIEW_ID).fileId(10L).filePath("Foo.java").build();
        ReviewFile rf2 = ReviewFile.builder().id(2L).reviewId(REVIEW_ID).fileId(20L).filePath("Bar.java").build();

        when(reviewFileRepository.findByReviewId(REVIEW_ID)).thenReturn(List.of(rf1, rf2));

        List<ReviewFile> result = reviewService.getReviewFiles(REVIEW_ID);

        assertEquals(2, result.size());
        assertEquals("Foo.java", result.get(0).getFilePath());
        assertEquals("Bar.java", result.get(1).getFilePath());
    }

    @Test
    void getReviewFiles_EmptyList_WhenNoFiles() {
        when(reviewFileRepository.findByReviewId(REVIEW_ID)).thenReturn(List.of());

        List<ReviewFile> result = reviewService.getReviewFiles(REVIEW_ID);

        assertTrue(result.isEmpty());
    }

    // --- Helper methods ---

    private MultiFileAnalysisResult buildMultiFileResult(ReviewResponse response) {
        return new MultiFileAnalysisResult(response, java.util.Map.of());
    }

    private Review buildSampleReview() {
        Review review = new Review();
        review.setId(REVIEW_ID);
        review.setUserId(USER_ID);
        review.setLanguage(VALID_LANGUAGE);
        review.setSourceType("paste");
        review.setCodeInput(VALID_CODE);
        review.setOverallRating("needs_improvement");
        review.setCriticalCount(1);
        review.setWarningCount(1);
        review.setSuggestionCount(1);
        review.setCreatedAt(LocalDateTime.of(2026, 1, 15, 10, 30));
        return review;
    }
}
