package com.codereview.controller;

import com.codereview.dto.CodebaseReviewResponse;
import com.codereview.dto.ReviewDTO;
import com.codereview.dto.ReviewResponse;
import com.codereview.entity.ReviewFile;
import com.codereview.exception.GlobalExceptionHandler;
import com.codereview.service.RateLimitService;
import com.codereview.service.ReviewService;
import com.codereview.service.JwtUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ReviewService reviewService;

    @Mock
    private RateLimitService rateLimitService;

    @InjectMocks
    private ReviewController reviewController;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String BASE_URL = "/api/reviews";
    private static final String EMAIL = "test@example.com";
    private static final Long USER_ID = 1L;
    private static final Long REVIEW_ID = 10L;

    private ReviewDTO sampleReviewDTO;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(reviewController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        sampleReviewDTO = new ReviewDTO();
        sampleReviewDTO.setId(REVIEW_ID);
        sampleReviewDTO.setLanguage("java");
        sampleReviewDTO.setSourceType("paste");
        sampleReviewDTO.setCodeInput("public class Test { }");
        sampleReviewDTO.setOverallRating("needs_improvement");
        sampleReviewDTO.setCriticalCount(1);
        sampleReviewDTO.setWarningCount(1);
        sampleReviewDTO.setSuggestionCount(1);
        sampleReviewDTO.setCreatedAt(LocalDateTime.of(2026, 1, 15, 10, 30));

        ReviewResponse response = new ReviewResponse();
        response.setOverallAssessment("needs_improvement");
        response.setIssues(List.of());
        response.setSummary("Test summary");
        sampleReviewDTO.setReviewResult(response);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate() {
        JwtUserDetails principal = new JwtUserDetails(USER_ID, EMAIL, "");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /**
     * Minimal UserDetails implementation for testing @AuthenticationPrincipal
     */

    // --- POST /api/reviews/analyze ---

    @Test
    void analyze_ValidRequest_ReturnsReviewDTO() throws Exception {
        authenticate();
        when(rateLimitService.isAllowed(USER_ID)).thenReturn(true);
        when(reviewService.analyzeAndSave(eq(USER_ID), anyString(), anyString())).thenReturn(sampleReviewDTO);

        mockMvc.perform(post(BASE_URL + "/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"public class Test { }\", \"language\": \"java\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(REVIEW_ID))
                .andExpect(jsonPath("$.language").value("java"))
                .andExpect(jsonPath("$.overallRating").value("needs_improvement"))
                .andExpect(jsonPath("$.criticalCount").value(1))
                .andExpect(jsonPath("$.warningCount").value(1))
                .andExpect(jsonPath("$.suggestionCount").value(1));

        verify(reviewService).analyzeAndSave(eq(USER_ID), eq("public class Test { }"), eq("java"));
    }

    @Test
    void analyze_MissingCode_ReturnsBadRequest() throws Exception {
        authenticate();

        mockMvc.perform(post(BASE_URL + "/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\": \"java\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void analyze_MissingLanguage_ReturnsBadRequest() throws Exception {
        authenticate();

        mockMvc.perform(post(BASE_URL + "/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"public class Test { }\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void analyze_EmptyCode_ReturnsBadRequest() throws Exception {
        authenticate();

        mockMvc.perform(post(BASE_URL + "/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"\", \"language\": \"java\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void analyze_RateLimitExceeded_ReturnsTooManyRequests() throws Exception {
        authenticate();
        when(rateLimitService.isAllowed(USER_ID)).thenReturn(false);
        when(rateLimitService.getRemainingRequests(USER_ID)).thenReturn(0L);
        when(rateLimitService.getTimeUntilReset(USER_ID)).thenReturn(3600L);

        mockMvc.perform(post(BASE_URL + "/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"public class Test { }\", \"language\": \"java\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Rate limit exceeded. Please try again later."))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.remainingRequests").value(0))
                .andExpect(jsonPath("$.retryAfterSeconds").value(3600));

        verify(reviewService, never()).analyzeAndSave(anyLong(), anyString(), anyString());
    }

    @Test
    void analyze_RateLimitAllowed_CallsAnalyzeAndSave() throws Exception {
        authenticate();
        when(rateLimitService.isAllowed(USER_ID)).thenReturn(true);
        when(reviewService.analyzeAndSave(eq(USER_ID), anyString(), anyString())).thenReturn(sampleReviewDTO);

        mockMvc.perform(post(BASE_URL + "/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"public class Test { }\", \"language\": \"java\"}"))
                .andExpect(status().isOk());

        verify(rateLimitService).isAllowed(USER_ID);
        verify(reviewService).analyzeAndSave(eq(USER_ID), anyString(), anyString());
    }

    // --- GET /api/reviews ---

    @Test
    void getReviews_DefaultParams_ReturnsList() throws Exception {
        authenticate();
        Page<ReviewDTO> page = new PageImpl<>(List.of(sampleReviewDTO), PageRequest.of(0, 20), 1);
        when(reviewService.getReviews(eq(USER_ID), eq(0), eq(20), isNull())).thenReturn(page);

        mockMvc.perform(get(BASE_URL)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(REVIEW_ID));
    }

    @Test
    void getReviews_WithLanguageFilter_PassesFilter() throws Exception {
        authenticate();
        Page<ReviewDTO> page = new PageImpl<>(List.of(sampleReviewDTO), PageRequest.of(0, 10), 1);
        when(reviewService.getReviews(eq(USER_ID), eq(0), eq(10), eq("python"))).thenReturn(page);

        mockMvc.perform(get(BASE_URL)
                        .param("page", "0")
                        .param("size", "10")
                        .param("language", "python"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void getReviews_EmptyResult_ReturnsEmptyList() throws Exception {
        authenticate();
        Page<ReviewDTO> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(reviewService.getReviews(eq(USER_ID), eq(0), eq(20), isNull())).thenReturn(emptyPage);

        mockMvc.perform(get(BASE_URL)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // --- GET /api/reviews/{id} ---

    @Test
    void getReview_Exists_ReturnsReviewDTO() throws Exception {
        authenticate();
        when(reviewService.getReview(USER_ID, REVIEW_ID)).thenReturn(sampleReviewDTO);

        mockMvc.perform(get(BASE_URL + "/" + REVIEW_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(REVIEW_ID))
                .andExpect(jsonPath("$.language").value("java"));
    }

    @Test
    void getReview_NotFound_ReturnsNotFound() throws Exception {
        authenticate();
        when(reviewService.getReview(USER_ID, REVIEW_ID))
                .thenThrow(new com.codereview.exception.ResourceNotFoundException("Review not found"));

        mockMvc.perform(get(BASE_URL + "/" + REVIEW_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Review not found"));
    }

    // --- DELETE /api/reviews/{id} ---

    @Test
    void deleteReview_Exists_ReturnsNoContent() throws Exception {
        authenticate();
        doNothing().when(reviewService).deleteReview(USER_ID, REVIEW_ID);

        mockMvc.perform(delete(BASE_URL + "/" + REVIEW_ID))
                .andExpect(status().isNoContent());

        verify(reviewService).deleteReview(USER_ID, REVIEW_ID);
    }

    @Test
    void deleteReview_NotFound_ReturnsNotFound() throws Exception {
        authenticate();
        doThrow(new com.codereview.exception.ResourceNotFoundException("Review not found")).when(reviewService).deleteReview(USER_ID, REVIEW_ID);

        mockMvc.perform(delete(BASE_URL + "/" + REVIEW_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Review not found"));
    }

    // --- POST /api/reviews/analyze-codebase ---

    @Test
    void analyzeCodebase_ValidRequest_ReturnsCodebaseReviewResponse() throws Exception {
        authenticate();
        when(rateLimitService.isAllowed(USER_ID)).thenReturn(true);

        CodebaseReviewResponse codebaseResponse = new CodebaseReviewResponse();
        codebaseResponse.setOverallAssessment("good");
        codebaseResponse.setIssues(List.of());
        codebaseResponse.setSummary("All files look good.");
        codebaseResponse.setTotalFiles(2);
        codebaseResponse.setFileBreakdowns(List.of());

        when(reviewService.analyzeCodebase(eq(USER_ID), eq(List.of(1L, 2L)))).thenReturn(codebaseResponse);

        mockMvc.perform(post(BASE_URL + "/analyze-codebase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileIds\": [1, 2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallAssessment").value("good"))
                .andExpect(jsonPath("$.totalFiles").value(2));

        verify(reviewService).analyzeCodebase(eq(USER_ID), eq(List.of(1L, 2L)));
    }

    @Test
    void analyzeCodebase_EmptyFileIds_ReturnsBadRequest() throws Exception {
        authenticate();

        mockMvc.perform(post(BASE_URL + "/analyze-codebase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileIds\": []}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void analyzeCodebase_RateLimitExceeded_ReturnsTooManyRequests() throws Exception {
        authenticate();
        when(rateLimitService.isAllowed(USER_ID)).thenReturn(false);
        when(rateLimitService.getRemainingRequests(USER_ID)).thenReturn(0L);
        when(rateLimitService.getTimeUntilReset(USER_ID)).thenReturn(3600L);

        mockMvc.perform(post(BASE_URL + "/analyze-codebase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileIds\": [1]}"))
                .andExpect(status().isTooManyRequests());

        verify(reviewService, never()).analyzeCodebase(anyLong(), anyList());
    }

    // --- POST /api/reviews/analyze-files ---

    @Test
    void analyzeFiles_ValidRequest_ReturnsReviewDTO() throws Exception {
        authenticate();
        when(rateLimitService.isAllowed(USER_ID)).thenReturn(true);
        when(reviewService.analyzeCodebaseContents(eq(USER_ID), anyList())).thenReturn(sampleReviewDTO);

        mockMvc.perform(post(BASE_URL + "/analyze-files")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\": [{\"name\": \"Foo.java\", \"language\": \"java\", \"content\": \"class Foo { }\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(REVIEW_ID));

        verify(reviewService).analyzeCodebaseContents(eq(USER_ID), anyList());
    }

    @Test
    void analyzeFiles_EmptyFiles_ReturnsBadRequest() throws Exception {
        authenticate();

        mockMvc.perform(post(BASE_URL + "/analyze-files")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\": []}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void analyzeFiles_RateLimitExceeded_ReturnsTooManyRequests() throws Exception {
        authenticate();
        when(rateLimitService.isAllowed(USER_ID)).thenReturn(false);
        when(rateLimitService.getRemainingRequests(USER_ID)).thenReturn(0L);
        when(rateLimitService.getTimeUntilReset(USER_ID)).thenReturn(3600L);

        mockMvc.perform(post(BASE_URL + "/analyze-files")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\": [{\"name\": \"Foo.java\", \"content\": \"class Foo { }\"}]}"))
                .andExpect(status().isTooManyRequests());

        verify(reviewService, never()).analyzeCodebaseContents(anyLong(), anyList());
    }

    // --- GET /api/reviews/{id}/files ---

    @Test
    void getReviewFiles_Exists_ReturnsFiles() throws Exception {
        authenticate();

        ReviewFile rf1 = ReviewFile.builder().id(1L).reviewId(REVIEW_ID).fileId(10L).filePath("Foo.java").build();
        ReviewFile rf2 = ReviewFile.builder().id(2L).reviewId(REVIEW_ID).fileId(20L).filePath("Bar.java").build();

        when(reviewService.getReviewFiles(REVIEW_ID)).thenReturn(List.of(rf1, rf2));

        mockMvc.perform(get(BASE_URL + "/" + REVIEW_ID + "/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].filePath").value("Foo.java"))
                .andExpect(jsonPath("$[1].filePath").value("Bar.java"));
    }

    @Test
    void getReviewFiles_EmptyList_ReturnsEmptyArray() throws Exception {
        authenticate();
        when(reviewService.getReviewFiles(REVIEW_ID)).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL + "/" + REVIEW_ID + "/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

}
