package com.codereview.controller;

import com.codereview.dto.*;
import com.codereview.entity.User;
import com.codereview.exception.GlobalExceptionHandler;
import com.codereview.repository.UserRepository;
import com.codereview.service.GithubService;
import com.codereview.service.RateLimitService;
import com.codereview.service.ReviewService;
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
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GithubControllerTest {

    private MockMvc mockMvc;

    @Mock
    private GithubService githubService;

    @Mock
    private ReviewService reviewService;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GithubController githubController;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String BASE_URL = "/api/github";
    private static final Long USER_ID = 1L;
    private static final String EMAIL = "test@example.com";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(githubController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        User user = new User();
        user.setId(USER_ID);
        user.setEmail(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate() {
        var principal = new com.codereview.service.JwtUserDetails(USER_ID, EMAIL, "password");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // --- GET /api/github/login ---

    @Test
    void getAuthUrl_LoginEndpoint_WhenConfigured_ReturnsUrl() throws Exception {
        authenticate();
        when(githubService.isConfigured()).thenReturn(true);
        when(githubService.getAuthorizationUrl()).thenReturn("https://github.com/login/oauth/authorize?client_id=test");

        mockMvc.perform(get(BASE_URL + "/login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", containsString("github.com")));
    }

    @Test
    void getAuthUrl_AuthUrlEndpoint_WhenConfigured_ReturnsUrl() throws Exception {
        authenticate();
        when(githubService.isConfigured()).thenReturn(true);
        when(githubService.getAuthorizationUrl()).thenReturn("https://github.com/login/oauth/authorize?client_id=test");

        mockMvc.perform(get(BASE_URL + "/auth-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", containsString("github.com")));
    }

    @Test
    void getAuthUrl_WhenNotConfigured_ReturnsServiceUnavailable() throws Exception {
        authenticate();
        when(githubService.isConfigured()).thenReturn(false);

        mockMvc.perform(get(BASE_URL + "/login"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error", containsString("not configured")));
    }

    // --- GET /api/github/callback ---

    @Test
    void handleCallback_Success_ReturnsConnectedMessage() throws Exception {
        authenticate();
        when(githubService.exchangeCodeForToken("auth-code")).thenReturn("ghp_token123");

        mockMvc.perform(get(BASE_URL + "/callback")
                        .param("code", "auth-code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("connected")))
                .andExpect(jsonPath("$.connected").value(true));

        verify(githubService).connectAccount(USER_ID, "ghp_token123");
    }

    // --- GET /api/github/status ---

    @Test
    void getStatus_ReturnsConfiguredStatus() throws Exception {
        authenticate();
        when(githubService.isConfigured()).thenReturn(true);

        mockMvc.perform(get(BASE_URL + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true));
    }

    // --- GET /api/github/repos ---

    @Test
    void getRepos_Success_ReturnsRepos() throws Exception {
        authenticate();
        GithubRepoDTO repo = new GithubRepoDTO();
        repo.setId(1L);
        repo.setName("test-repo");
        when(githubService.getRepositories(USER_ID)).thenReturn(List.of(repo));

        mockMvc.perform(get(BASE_URL + "/repos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("test-repo"));
    }

    // --- GET /api/github/repos/{owner}/{repo}/pulls ---

    @Test
    void getPullRequests_Success_ReturnsPRs() throws Exception {
        authenticate();
        GithubPRDTO pr = new GithubPRDTO();
        pr.setNumber(1);
        pr.setTitle("Fix bug");
        pr.setState("open");
        when(githubService.getPullRequests(USER_ID, "owner", "repo")).thenReturn(List.of(pr));

        mockMvc.perform(get(BASE_URL + "/repos/owner/repo/pulls"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].number").value(1))
                .andExpect(jsonPath("$[0].title").value("Fix bug"));
    }

    // --- POST /api/github/review-pr ---

    @Test
    void reviewPR_Success_ReturnsFileReviews() throws Exception {
        authenticate();
        when(rateLimitService.isAllowed(USER_ID)).thenReturn(true);
        when(rateLimitService.getMaxReviewsPerDay()).thenReturn(50);
        when(rateLimitService.getRemainingRequests(USER_ID)).thenReturn(49L);

        Map<String, String> file = Map.of(
                "filename", "Main.java",
                "status", "modified",
                "patch", "@@ -1 +1 @@\n-old\n+new"
        );
        when(githubService.getPRFiles(USER_ID, "owner", "repo", 1)).thenReturn(List.of(file));

        ReviewDTO reviewDTO = new ReviewDTO();
        reviewDTO.setId(1L);
        reviewDTO.setOverallRating("good");
        when(reviewService.analyzeAndSave(eq(USER_ID), anyString(), eq("java"))).thenReturn(reviewDTO);

        mockMvc.perform(post(BASE_URL + "/review-pr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\": \"owner\", \"repo\": \"repo\", \"prNumber\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prNumber").value(1))
                .andExpect(jsonPath("$.repo").value("owner/repo"))
                .andExpect(jsonPath("$.fileReviews", hasSize(1)));
    }

    @Test
    void reviewPR_RateLimited_Returns429() throws Exception {
        authenticate();
        when(rateLimitService.isAllowed(USER_ID)).thenReturn(false);
        when(rateLimitService.getTimeUntilReset(USER_ID)).thenReturn(3600L);

        mockMvc.perform(post(BASE_URL + "/review-pr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\": \"owner\", \"repo\": \"repo\", \"prNumber\": 1}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3600"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(3600));
    }

    @Test
    void reviewPR_SkipsFilesWithoutPatch() throws Exception {
        authenticate();
        when(rateLimitService.isAllowed(USER_ID)).thenReturn(true);
        when(rateLimitService.getMaxReviewsPerDay()).thenReturn(50);
        when(rateLimitService.getRemainingRequests(USER_ID)).thenReturn(49L);

        Map<String, String> binaryFile = Map.of(
                "filename", "image.png",
                "status", "modified"
                // No patch for binary files
        );
        when(githubService.getPRFiles(USER_ID, "owner", "repo", 1)).thenReturn(List.of(binaryFile));

        mockMvc.perform(post(BASE_URL + "/review-pr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\": \"owner\", \"repo\": \"repo\", \"prNumber\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileReviews", hasSize(0)));

        // Should not call review service for binary files
        verify(reviewService, never()).analyzeAndSave(anyLong(), anyString(), anyString());
    }
}
