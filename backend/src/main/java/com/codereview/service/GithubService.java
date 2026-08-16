package com.codereview.service;

import com.codereview.dto.*;
import com.codereview.entity.User;
import com.codereview.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class GithubService {

    private final UserRepository userRepository;
    private final RestTemplate restTemplate;
    private final TokenEncryptionService tokenEncryptionService;
    private final StringRedisTemplate redisTemplate;

    @Value("${github.client-id:}")
    private String clientId;

    @Value("${github.client-secret:}")
    private String clientSecret;

    @Value("${github.redirect-uri:}")
    private String redirectUri;

    private static final String GITHUB_API = "https://api.github.com";
    private static final String OAUTH_STATE_PREFIX = "github:oauth_state:";
    private static final long OAUTH_STATE_TTL_MINUTES = 10;
    private static final Pattern GITHUB_PATH_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");

    /**
     * Check if GitHub integration is properly configured.
     */
    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && !clientId.equals("placeholder")
                && clientSecret != null && !clientSecret.isBlank()
                && !clientSecret.equals("placeholder");
    }

    /**
     * Build the GitHub OAuth authorization URL.
     * Stores the state in Redis with 10-minute TTL for CSRF protection.
     * @throws IllegalStateException if GitHub is not configured
     */
    public String getAuthorizationUrl() {
        validateConfig();
        String state = UUID.randomUUID().toString();

        // Store state in Redis with 10-minute TTL
        try {
            String redisKey = OAUTH_STATE_PREFIX + state;
            redisTemplate.opsForValue().set(redisKey, state, OAUTH_STATE_TTL_MINUTES, TimeUnit.MINUTES);
            log.debug("OAuth state stored in Redis: {}", state);
        } catch (Exception e) {
            log.error("Failed to store OAuth state in Redis: {}", e.getMessage());
            throw new RuntimeException("Failed to initiate OAuth flow", e);
        }

        return UriComponentsBuilder.fromHttpUrl("https://github.com/login/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "repo,user")
                .queryParam("state", state)
                .toUriString();
    }

    /**
     * Validate the OAuth state parameter against the stored state in Redis.
     *
     * @param state the state parameter from the callback
     * @return true if the state is valid and was not reused
     * @throws RuntimeException if the state is missing, expired, or invalid
     */
    public boolean validateOAuthState(String state) {
        if (state == null || state.isBlank()) {
            throw new RuntimeException("OAuth state parameter is missing");
        }

        try {
            String redisKey = OAUTH_STATE_PREFIX + state;
            String storedState = redisTemplate.opsForValue().get(redisKey);

            if (storedState == null) {
                log.warn("OAuth state not found or expired: {}", state);
                throw new RuntimeException("OAuth state is invalid or has expired. Please try again.");
            }

            // Delete the state to prevent replay attacks
            redisTemplate.delete(redisKey);
            log.debug("OAuth state validated and consumed: {}", state);
            return true;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to validate OAuth state: {}", e.getMessage());
            throw new RuntimeException("Failed to validate OAuth state", e);
        }
    }

    /**
     * Exchange OAuth code for access token.
     * @throws RuntimeException if exchange fails
     */
    public String exchangeCodeForToken(String code) {
        validateConfig();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        Map<String, String> body = new HashMap<>();
        body.put("client_id", clientId);
        body.put("client_secret", clientSecret);
        body.put("code", code);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://github.com/login/oauth/access_token", request, Map.class);

            if (response.getBody() != null && response.getBody().containsKey("access_token")) {
                return (String) response.getBody().get("access_token");
            }

            // GitHub may return error in body
            String error = response.getBody() != null
                    ? (String) response.getBody().get("error_description")
                    : "No response body";
            log.error("GitHub token exchange failed: {}", error);
            throw new RuntimeException("Failed to exchange code for token: " + error);
        } catch (HttpClientErrorException e) {
            log.error("GitHub token exchange HTTP error: {}", e.getMessage());
            throw new RuntimeException("Failed to exchange code for token: " + e.getMessage());
        }
    }

    /**
     * Connect a GitHub account to a local user by verifying the token
     * and fetching the GitHub user profile.
     * Token is encrypted before storing in the database.
     */
    public void connectAccount(Long userId, String token) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    GITHUB_API + "/user", HttpMethod.GET, request, Map.class);

            if (response.getBody() != null) {
                user.setGithubId(((Number) response.getBody().get("id")).longValue());
                user.setGithubUsername((String) response.getBody().get("login"));
                // Encrypt the token before storing
                user.setGithubToken(tokenEncryptionService.encrypt(token));
                userRepository.save(user);
                log.info("GitHub account connected for user {}", userId);
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 401) {
                throw new RuntimeException("Invalid GitHub access token");
            }
            log.error("Failed to verify GitHub token: {}", e.getMessage());
            throw new RuntimeException("Failed to verify GitHub token: " + e.getMessage());
        }
    }

    /**
     * Get repositories for a connected GitHub user.
     */
    public List<GithubRepoDTO> getRepositories(Long userId) {
        String decryptedToken = getDecryptedTokenForUser(userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(decryptedToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<GithubRepoDTO[]> response = restTemplate.exchange(
                    GITHUB_API + "/user/repos?sort=updated&per_page=30",
                    HttpMethod.GET, request, GithubRepoDTO[].class);

            return Arrays.asList(response.getBody() != null ? response.getBody() : new GithubRepoDTO[0]);
        } catch (HttpClientErrorException e) {
            handleGithubApiError(e);
            return List.of();
        }
    }

    /**
     * Get branches for a repository.
     */
    public List<GithubBranchDTO> getBranches(Long userId, String owner, String repo) {
        validateGitHubPath(owner);
        validateGitHubPath(repo);
        String decryptedToken = getDecryptedTokenForUser(userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(decryptedToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map[]> response = restTemplate.exchange(
                    GITHUB_API + "/repos/" + owner + "/" + repo + "/branches?per_page=100",
                    HttpMethod.GET, request, Map[].class);

            List<GithubBranchDTO> branches = new ArrayList<>();
            if (response.getBody() != null) {
                for (Map branch : response.getBody()) {
                    GithubBranchDTO dto = new GithubBranchDTO();
                    dto.setName((String) branch.get("name"));
                    Object protection = branch.get("protected");
                    // The "protected" field tells us if branch protection is on
                    // The default branch info comes from the repo itself
                    branches.add(dto);
                }
            }
            return branches;
        } catch (HttpClientErrorException e) {
            handleGithubApiError(e);
            return List.of();
        }
    }

    /**
     * Get open pull requests for a repository.
     */
    public List<GithubPRDTO> getPullRequests(Long userId, String owner, String repo) {
        validateGitHubPath(owner);
        validateGitHubPath(repo);
        String decryptedToken = getDecryptedTokenForUser(userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(decryptedToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map[]> response = restTemplate.exchange(
                    GITHUB_API + "/repos/" + owner + "/" + repo + "/pulls?state=open&per_page=30",
                    HttpMethod.GET, request, Map[].class);

            List<GithubPRDTO> prs = new ArrayList<>();
            if (response.getBody() != null) {
                for (Map pr : response.getBody()) {
                    GithubPRDTO dto = new GithubPRDTO();
                    dto.setNumber(((Number) pr.get("number")).intValue());
                    dto.setTitle((String) pr.get("title"));
                    dto.setState((String) pr.get("state"));
                    dto.setHtmlUrl((String) pr.get("html_url"));
                    dto.setBody((String) pr.get("body"));
                    dto.setCreatedAt((String) pr.get("created_at"));

                    Map head = (Map) pr.get("head");
                    Map base = (Map) pr.get("base");
                    if (head != null) dto.setHeadBranch((String) head.get("ref"));
                    if (base != null) dto.setBaseBranch((String) base.get("ref"));

                    Map userMap = (Map) pr.get("user");
                    if (userMap != null) {
                        GithubPRDTO.GithubUserDTO userDTO = new GithubPRDTO.GithubUserDTO();
                        userDTO.setLogin((String) userMap.get("login"));
                        dto.setUser(userDTO);
                    }

                    prs.add(dto);
                }
            }
            return prs;
        } catch (HttpClientErrorException e) {
            handleGithubApiError(e);
            return List.of();
        }
    }

    /**
     * Get the diff for a pull request.
     */
    public String getPRDiff(Long userId, String owner, String repo, int prNumber) {
        validateGitHubPath(owner);
        validateGitHubPath(repo);
        String decryptedToken = getDecryptedTokenForUser(userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(decryptedToken);
        headers.setAccept(List.of(MediaType.parseMediaType("application/vnd.github.v3.diff")));
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    GITHUB_API + "/repos/" + owner + "/" + repo + "/pulls/" + prNumber,
                    HttpMethod.GET, request, String.class);

            return response.getBody();
        } catch (HttpClientErrorException e) {
            handleGithubApiError(e);
            return null;
        }
    }

    /**
     * Get files changed in a pull request.
     */
    public List<Map<String, String>> getPRFiles(Long userId, String owner, String repo, int prNumber) {
        validateGitHubPath(owner);
        validateGitHubPath(repo);
        String decryptedToken = getDecryptedTokenForUser(userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(decryptedToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map[]> response = restTemplate.exchange(
                    GITHUB_API + "/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/files?per_page=100",
                    HttpMethod.GET, request, Map[].class);

            List<Map<String, String>> files = new ArrayList<>();
            if (response.getBody() != null) {
                for (Map file : response.getBody()) {
                    Map<String, String> fileInfo = new HashMap<>();
                    fileInfo.put("filename", (String) file.get("filename"));
                    fileInfo.put("status", (String) file.get("status"));
                    fileInfo.put("patch", (String) file.get("patch"));
                    files.add(fileInfo);
                }
            }
            return files;
        } catch (HttpClientErrorException e) {
            handleGithubApiError(e);
            return List.of();
        }
    }

    /**
     * Get the decrypted GitHub token for a user without modifying the managed JPA entity.
     * This prevents the plaintext token from being accidentally persisted by Hibernate.
     *
     * @param userId the user's ID
     * @return the decrypted GitHub token
     * @throws RuntimeException if user not found or GitHub account not connected
     */
    private String getDecryptedTokenForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getGithubToken() == null || user.getGithubToken().isBlank()) {
            throw new RuntimeException("GitHub account not connected");
        }

        return tokenEncryptionService.decrypt(user.getGithubToken());
    }

    /**
     * Validate that a GitHub path segment (owner or repo name) contains only
     * safe characters to prevent SSRF via path injection.
     *
     * @param value the path segment to validate
     * @throws IllegalArgumentException if the value contains invalid characters
     */
    private void validateGitHubPath(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("GitHub path parameter must not be blank");
        }
        if (!GITHUB_PATH_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid GitHub path parameter: " + value);
        }
    }

    /**
     * Validate that GitHub OAuth config is present and not placeholder.
     */
    private void validateConfig() {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "GitHub integration is not configured. " +
                    "Set GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET environment variables.");
        }
    }

    /**
     * Handle GitHub API errors with appropriate messages.
     */
    private void handleGithubApiError(HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        switch (status) {
            case 401 -> {
                log.error("GitHub API authentication failed (401)");
                throw new RuntimeException("GitHub authentication failed. Please reconnect your account.");
            }
            case 403 -> {
                log.warn("GitHub API rate limit exceeded (403)");
                String resetHeader = e.getResponseHeaders() != null
                        ? e.getResponseHeaders().getFirst("X-RateLimit-Reset")
                        : null;
                throw new RuntimeException("GitHub API rate limit exceeded. "
                        + (resetHeader != null ? "Resets at: " + resetHeader : "Try again later."));
            }
            case 404 -> {
                log.warn("GitHub API resource not found (404)");
                throw new RuntimeException("GitHub repository or resource not found.");
            }
            case 422 -> {
                log.warn("GitHub API validation error (422): {}", e.getMessage());
                throw new RuntimeException("GitHub API validation error: " + e.getMessage());
            }
            default -> {
                log.error("GitHub API error {}: {}", status, e.getMessage());
                throw new RuntimeException("GitHub API error (" + status + "): " + e.getMessage());
            }
        }
    }
}
