package com.codereview.controller;

import com.codereview.dto.*;
import com.codereview.service.GithubService;
import com.codereview.service.RateLimitService;
import com.codereview.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/github")
@RequiredArgsConstructor
public class GithubController {

    private final GithubService githubService;
    private final ReviewService reviewService;
    private final RateLimitService rateLimitService;

    /**
     * GET /api/github/login (alias) or /api/github/auth-url
     * Returns the GitHub OAuth authorization URL.
     * Also returns configuration status so the frontend knows if GitHub is set up.
     */
    @GetMapping({"/login", "/auth-url"})
    public ResponseEntity<Map<String, String>> getAuthUrl() {
        if (!githubService.isConfigured()) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "GitHub integration is not configured on the server");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
        return ResponseEntity.ok(Map.of("url", githubService.getAuthorizationUrl()));
    }

    /**
     * GET /api/github/callback?code=...&state=...
     * Handles the OAuth callback, exchanges code for token, connects account.
     * Validates the state parameter for CSRF protection.
     */
    @GetMapping("/callback")
    public ResponseEntity<Map<String, Object>> handleCallback(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String code,
            @RequestParam(required = false) String state) {
        Long userId = extractUserId(userDetails);

        // Validate OAuth state parameter
        githubService.validateOAuthState(state);

        String token = githubService.exchangeCodeForToken(code);
        githubService.connectAccount(userId, token);
        return ResponseEntity.ok(Map.of(
                "message", "GitHub account connected successfully",
                "connected", true
        ));
    }

    /**
     * GET /api/github/status
     * Check if the user's GitHub account is connected.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(Map.of(
                "configured", githubService.isConfigured()
        ));
    }

    /**
     * GET /api/github/repos
     * List repositories for the authenticated user.
     */
    @GetMapping("/repos")
    public ResponseEntity<List<GithubRepoDTO>> getRepos(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = extractUserId(userDetails);
        return ResponseEntity.ok(githubService.getRepositories(userId));
    }

    /**
     * GET /api/github/repos/{owner}/{repo}/branches
     */
    @GetMapping("/repos/{owner}/{repo}/branches")
    public ResponseEntity<List<GithubBranchDTO>> getBranches(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String owner,
            @PathVariable String repo) {
        Long userId = extractUserId(userDetails);
        return ResponseEntity.ok(githubService.getBranches(userId, owner, repo));
    }

    /**
     * GET /api/github/repos/{owner}/{repo}/pulls
     */
    @GetMapping("/repos/{owner}/{repo}/pulls")
    public ResponseEntity<List<GithubPRDTO>> getPullRequests(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String owner,
            @PathVariable String repo) {
        Long userId = extractUserId(userDetails);
        return ResponseEntity.ok(githubService.getPullRequests(userId, owner, repo));
    }

    /**
     * GET /api/github/repos/{owner}/{repo}/pulls/{pr}/diff
     */
    @GetMapping("/repos/{owner}/{repo}/pulls/{pr}/diff")
    public ResponseEntity<String> getPRDiff(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable int pr) {
        Long userId = extractUserId(userDetails);
        return ResponseEntity.ok(githubService.getPRDiff(userId, owner, repo, pr));
    }

    /**
     * GET /api/github/repos/{owner}/{repo}/pulls/{pr}/files
     */
    @GetMapping("/repos/{owner}/{repo}/pulls/{pr}/files")
    public ResponseEntity<List<Map<String, String>>> getPRFiles(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable int pr) {
        Long userId = extractUserId(userDetails);
        return ResponseEntity.ok(githubService.getPRFiles(userId, owner, repo, pr));
    }

    /**
     * POST /api/github/review-pr
     * Review a pull request by analyzing all changed files.
     */
    @PostMapping("/review-pr")
    public ResponseEntity<Map<String, Object>> reviewPR(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody PRReviewRequest request) {
        Long userId = extractUserId(userDetails);

        // Check rate limit
        if (!rateLimitService.isAllowed(userId)) {
            long timeUntilReset = rateLimitService.getTimeUntilReset(userId);
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("message", "Rate limit exceeded. Please try again later.");
            errorBody.put("retryAfterSeconds", timeUntilReset);
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(timeUntilReset))
                    .body(errorBody);
        }

        List<Map<String, String>> files = githubService.getPRFiles(
                userId, request.getOwner(), request.getRepo(), request.getPrNumber());

        List<Object> results = new ArrayList<>();
        for (Map<String, String> file : files) {
            String filename = file.get("filename");
            String patch = file.get("patch");

            if (patch == null || patch.isBlank()) {
                continue; // Skip files without patches (binary files, etc.)
            }

            String language = detectLanguage(filename);
            try {
                var review = reviewService.analyzeAndSave(userId, patch, language);
                Map<String, Object> fileResult = new HashMap<>();
                fileResult.put("filename", filename);
                fileResult.put("review", review);
                results.add(fileResult);
            } catch (Exception e) {
                Map<String, Object> fileResult = new HashMap<>();
                fileResult.put("filename", filename);
                fileResult.put("error", "Failed to review: " + e.getMessage());
                results.add(fileResult);
            }
        }

        return ResponseEntity.ok(Map.of(
                "prNumber", request.getPrNumber(),
                "repo", request.getOwner() + "/" + request.getRepo(),
                "fileReviews", results
        ));
    }

    private Long extractUserId(UserDetails userDetails) {
        if (userDetails instanceof com.codereview.service.JwtUserDetails jwtUser) {
            return jwtUser.getUserId();
        }
        throw new RuntimeException("Invalid authentication");
    }

    private String detectLanguage(String filename) {
        if (filename == null) return "plaintext";
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0) return "plaintext";
        String ext = filename.substring(dotIndex + 1).toLowerCase();
        return switch (ext) {
            case "java" -> "java";
            case "py" -> "python";
            case "js" -> "javascript";
            case "ts" -> "typescript";
            case "jsx" -> "javascript";
            case "tsx" -> "typescript";
            case "cpp", "cc", "cxx" -> "cpp";
            case "c", "h" -> "c";
            case "cs" -> "csharp";
            case "go" -> "go";
            case "rb" -> "ruby";
            case "rs" -> "rust";
            case "kt" -> "java";
            case "swift" -> "java"; // closest supported
            case "php" -> "php";
            default -> "plaintext";
        };
    }
}
