package com.codereview.controller;

import com.codereview.dto.ReviewDTO;
import com.codereview.exception.GlobalExceptionHandler;
import com.codereview.service.ReviewHistoryService;
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
class HistoryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ReviewHistoryService reviewHistoryService;

    @InjectMocks
    private HistoryController historyController;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String BASE_URL = "/api/history";
    private static final String EMAIL = "test@example.com";
    private static final Long USER_ID = 1L;
    private static final Long REVIEW_ID = 10L;

    private ReviewDTO sampleReviewDTO;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(historyController)
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

    // --- GET /api/history ---

    @Test
    void getReviewHistory_DefaultParams_ReturnsPage() throws Exception {
        authenticate();
        Page<ReviewDTO> page = new PageImpl<>(List.of(sampleReviewDTO), PageRequest.of(0, 20), 1);
        when(reviewHistoryService.getReviewHistory(eq(USER_ID), eq(0), eq(20), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(page);

        mockMvc.perform(get(BASE_URL)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(REVIEW_ID))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getReviewHistory_WithLanguageFilter_PassesFilter() throws Exception {
        authenticate();
        Page<ReviewDTO> page = new PageImpl<>(List.of(sampleReviewDTO), PageRequest.of(0, 10), 1);
        when(reviewHistoryService.getReviewHistory(eq(USER_ID), eq(0), eq(10), eq("python"), isNull(), isNull(), isNull()))
                .thenReturn(page);

        mockMvc.perform(get(BASE_URL)
                        .param("page", "0")
                        .param("size", "10")
                        .param("language", "python"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void getReviewHistory_WithAssessmentFilter_PassesFilter() throws Exception {
        authenticate();
        Page<ReviewDTO> page = new PageImpl<>(List.of(sampleReviewDTO), PageRequest.of(0, 20), 1);
        when(reviewHistoryService.getReviewHistory(eq(USER_ID), eq(0), eq(20), isNull(), eq("good"), isNull(), isNull()))
                .thenReturn(page);

        mockMvc.perform(get(BASE_URL)
                        .param("page", "0")
                        .param("size", "20")
                        .param("assessment", "good"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void getReviewHistory_WithDateRange_PassesFilters() throws Exception {
        authenticate();
        Page<ReviewDTO> page = new PageImpl<>(List.of(sampleReviewDTO), PageRequest.of(0, 20), 1);
        when(reviewHistoryService.getReviewHistory(eq(USER_ID), eq(0), eq(20), isNull(), isNull(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get(BASE_URL)
                        .param("page", "0")
                        .param("size", "20")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void getReviewHistory_EmptyResult_ReturnsEmptyPage() throws Exception {
        authenticate();
        Page<ReviewDTO> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(reviewHistoryService.getReviewHistory(eq(USER_ID), eq(0), eq(20), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(emptyPage);

        mockMvc.perform(get(BASE_URL)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // --- GET /api/history/{id} ---

    @Test
    void getReviewDetail_Exists_ReturnsReviewDTO() throws Exception {
        authenticate();
        when(reviewHistoryService.getReviewDetail(USER_ID, REVIEW_ID)).thenReturn(sampleReviewDTO);

        mockMvc.perform(get(BASE_URL + "/" + REVIEW_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(REVIEW_ID))
                .andExpect(jsonPath("$.language").value("java"));
    }

    @Test
    void getReviewDetail_NotFound_ReturnsNotFound() throws Exception {
        authenticate();
        when(reviewHistoryService.getReviewDetail(USER_ID, REVIEW_ID))
                .thenThrow(new com.codereview.exception.ResourceNotFoundException("Review not found"));

        mockMvc.perform(get(BASE_URL + "/" + REVIEW_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Review not found"));
    }

    @Test
    void getReviewDetail_MultipleReviews_ReturnsCorrectOne() throws Exception {
        authenticate();
        ReviewDTO anotherReviewDTO = new ReviewDTO();
        anotherReviewDTO.setId(20L);
        anotherReviewDTO.setLanguage("python");
        anotherReviewDTO.setSourceType("paste");
        anotherReviewDTO.setCodeInput("print('hello')");
        anotherReviewDTO.setOverallRating("good");
        anotherReviewDTO.setCreatedAt(LocalDateTime.of(2026, 1, 20, 14, 0));

        when(reviewHistoryService.getReviewDetail(USER_ID, 20L)).thenReturn(anotherReviewDTO);

        mockMvc.perform(get(BASE_URL + "/20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20))
                .andExpect(jsonPath("$.language").value("python"));
    }

    // --- DELETE /api/history/{id} ---

    @Test
    void deleteReview_Exists_ReturnsNoContent() throws Exception {
        authenticate();
        doNothing().when(reviewHistoryService).deleteReview(USER_ID, REVIEW_ID);

        mockMvc.perform(delete(BASE_URL + "/" + REVIEW_ID))
                .andExpect(status().isNoContent());

        verify(reviewHistoryService).deleteReview(USER_ID, REVIEW_ID);
    }

    @Test
    void deleteReview_NotFound_ReturnsNotFound() throws Exception {
        authenticate();
        doThrow(new com.codereview.exception.ResourceNotFoundException("Review not found"))
                .when(reviewHistoryService).deleteReview(USER_ID, REVIEW_ID);

        mockMvc.perform(delete(BASE_URL + "/" + REVIEW_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Review not found"));
    }
}
