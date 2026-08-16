package com.codereview.service;

import com.codereview.dto.GithubRepoDTO;
import com.codereview.dto.GithubPRDTO;
import com.codereview.dto.GithubBranchDTO;
import com.codereview.entity.User;
import com.codereview.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GithubServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private GithubService githubService;

    private static final Long USER_ID = 1L;
    private static final String GITHUB_TOKEN = "ghp_test123";
    private static final String CLIENT_ID = "test-client-id";
    private static final String CLIENT_SECRET = "test-client-secret";
    private static final String REDIRECT_URI = "http://localhost:8080/api/github/callback";

    private User connectedUser;

    @BeforeEach
    void setUp() {
        connectedUser = new User();
        connectedUser.setId(USER_ID);
        connectedUser.setGithubToken(GITHUB_TOKEN);
        connectedUser.setGithubUsername("testuser");
        connectedUser.setGithubId(12345L);

        // Set configured values via reflection
        setField(githubService, "clientId", CLIENT_ID);
        setField(githubService, "clientSecret", CLIENT_SECRET);
        setField(githubService, "redirectUri", REDIRECT_URI);

        // Mock Redis operations for OAuth state
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // --- isConfigured tests ---

    @Test
    void isConfigured_WithValidConfig_ReturnsTrue() {
        assertTrue(githubService.isConfigured());
    }

    @Test
    void isConfigured_WithBlankClientId_ReturnsFalse() {
        setField(githubService, "clientId", "");
        assertFalse(githubService.isConfigured());
    }

    @Test
    void isConfigured_WithPlaceholderClientId_ReturnsFalse() {
        setField(githubService, "clientId", "placeholder");
        assertFalse(githubService.isConfigured());
    }

    @Test
    void isConfigured_WithNullClientId_ReturnsFalse() {
        setField(githubService, "clientId", null);
        assertFalse(githubService.isConfigured());
    }

    @Test
    void isConfigured_WithPlaceholderSecret_ReturnsFalse() {
        setField(githubService, "clientSecret", "placeholder");
        assertFalse(githubService.isConfigured());
    }

    // --- getAuthorizationUrl tests ---

    @Test
    void getAuthorizationUrl_WhenConfigured_ReturnsUrl() {
        String url = githubService.getAuthorizationUrl();
        assertNotNull(url);
        assertTrue(url.contains("github.com/login/oauth/authorize"));
        assertTrue(url.contains("client_id=" + CLIENT_ID));
        assertTrue(url.contains("redirect_uri="));
        assertTrue(url.contains("scope=repo"));
        assertTrue(url.contains("state="));
        // Verify state was stored in Redis
        verify(valueOperations).set(anyString(), anyString(), eq(10L), eq(TimeUnit.MINUTES));
    }

    @Test
    void getAuthorizationUrl_WhenNotConfigured_ThrowsException() {
        setField(githubService, "clientId", "");
        assertThrows(IllegalStateException.class, () -> githubService.getAuthorizationUrl());
    }

    // --- exchangeCodeForToken tests ---

    @Test
    void exchangeCodeForToken_Success_ReturnsToken() {
        Map<String, String> responseBody = Map.of("access_token", "ghp_newtoken123");
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        String token = githubService.exchangeCodeForToken("auth-code");

        assertEquals("ghp_newtoken123", token);
    }

    @Test
    void exchangeCodeForToken_NoAccessToken_ThrowsException() {
        Map<String, String> responseBody = Map.of("error", "bad_verification_code");
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        assertThrows(RuntimeException.class, () -> githubService.exchangeCodeForToken("bad-code"));
    }

    @Test
    void exchangeCodeForToken_HttpError_ThrowsException() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST, "Bad Request",
                        HttpHeaders.EMPTY, new byte[0], null));

        assertThrows(RuntimeException.class, () -> githubService.exchangeCodeForToken("code"));
    }

    // --- connectAccount tests ---

    @Test
    void connectAccount_Success_SavesUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(connectedUser));
        when(tokenEncryptionService.encrypt(GITHUB_TOKEN)).thenReturn("encrypted-token");

        Map<String, Object> githubUser = Map.of(
                "id", 12345,
                "login", "testuser"
        );
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(githubUser, HttpStatus.OK));

        githubService.connectAccount(USER_ID, GITHUB_TOKEN);

        verify(userRepository).save(connectedUser);
        assertEquals(12345L, connectedUser.getGithubId());
        assertEquals("testuser", connectedUser.getGithubUsername());
        assertEquals("encrypted-token", connectedUser.getGithubToken());
    }

    @Test
    void connectAccount_UserNotFound_ThrowsException() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> githubService.connectAccount(USER_ID, GITHUB_TOKEN));
    }

    @Test
    void connectAccount_InvalidToken_ThrowsException() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(connectedUser));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.UNAUTHORIZED, "Unauthorized",
                        HttpHeaders.EMPTY, new byte[0], null));

        assertThrows(RuntimeException.class,
                () -> githubService.connectAccount(USER_ID, "invalid-token"));
    }

    // --- getRepositories tests ---

    @Test
    void getRepositories_Success_ReturnsRepos() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(connectedUser));
        when(tokenEncryptionService.decrypt(GITHUB_TOKEN)).thenReturn(GITHUB_TOKEN);

        GithubRepoDTO[] repos = new GithubRepoDTO[1];
        repos[0] = new GithubRepoDTO();
        repos[0].setId(1L);
        repos[0].setName("test-repo");
        repos[0].setFullName("testuser/test-repo");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(GithubRepoDTO[].class)))
                .thenReturn(new ResponseEntity<>(repos, HttpStatus.OK));

        List<GithubRepoDTO> result = githubService.getRepositories(USER_ID);

        assertEquals(1, result.size());
        assertEquals("test-repo", result.get(0).getName());
    }

    @Test
    void getRepositories_NoToken_ThrowsException() {
        User noTokenUser = new User();
        noTokenUser.setId(USER_ID);
        noTokenUser.setGithubToken(null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(noTokenUser));

        assertThrows(RuntimeException.class, () -> githubService.getRepositories(USER_ID));
    }

    @Test
    void getRepositories_BlankToken_ThrowsException() {
        User blankTokenUser = new User();
        blankTokenUser.setId(USER_ID);
        blankTokenUser.setGithubToken("  ");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(blankTokenUser));

        assertThrows(RuntimeException.class, () -> githubService.getRepositories(USER_ID));
    }

    @Test
    void getRepositories_ApiError_ThrowsException() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(connectedUser));
        when(tokenEncryptionService.decrypt(GITHUB_TOKEN)).thenReturn(GITHUB_TOKEN);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(GithubRepoDTO[].class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.FORBIDDEN, "Forbidden",
                        HttpHeaders.EMPTY, new byte[0], null));

        assertThrows(RuntimeException.class, () -> githubService.getRepositories(USER_ID));
    }

    // --- getPullRequests tests ---

    @Test
    void getPullRequests_Success_ReturnsPRs() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(connectedUser));
        when(tokenEncryptionService.decrypt(GITHUB_TOKEN)).thenReturn(GITHUB_TOKEN);

        Map<String, Object> pr1 = Map.of(
                "number", 1,
                "title", "Fix bug",
                "state", "open",
                "html_url", "https://github.com/test/repo/pull/1",
                "body", "Fix description",
                "created_at", "2026-01-15T10:00:00Z",
                "head", Map.of("ref", "feature-branch"),
                "base", Map.of("ref", "main"),
                "user", Map.of("login", "testuser")
        );

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map[].class)))
                .thenReturn(new ResponseEntity<>(new Map[]{pr1}, HttpStatus.OK));

        List<GithubPRDTO> result = githubService.getPullRequests(USER_ID, "owner", "repo");

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getNumber());
        assertEquals("Fix bug", result.get(0).getTitle());
        assertEquals("feature-branch", result.get(0).getHeadBranch());
        assertEquals("main", result.get(0).getBaseBranch());
        assertEquals("testuser", result.get(0).getUser().getLogin());
    }

    // --- getBranches tests ---

    @Test
    void getBranches_Success_ReturnsBranches() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(connectedUser));
        when(tokenEncryptionService.decrypt(GITHUB_TOKEN)).thenReturn(GITHUB_TOKEN);

        Map<String, Object> branch1 = Map.of("name", "main", "protected", false);
        Map<String, Object> branch2 = Map.of("name", "develop", "protected", false);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map[].class)))
                .thenReturn(new ResponseEntity<>(new Map[]{branch1, branch2}, HttpStatus.OK));

        List<GithubBranchDTO> result = githubService.getBranches(USER_ID, "owner", "repo");

        assertEquals(2, result.size());
        assertEquals("main", result.get(0).getName());
        assertEquals("develop", result.get(1).getName());
    }

    // --- getPRDiff tests ---

    @Test
    void getPRDiff_Success_ReturnsDiff() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(connectedUser));
        when(tokenEncryptionService.decrypt(GITHUB_TOKEN)).thenReturn(GITHUB_TOKEN);

        String diffContent = "--- a/file.java\n+++ b/file.java\n@@ -1 +1 @@\n-old\n+new";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(diffContent, HttpStatus.OK));

        String result = githubService.getPRDiff(USER_ID, "owner", "repo", 1);

        assertEquals(diffContent, result);
    }

    // --- getPRFiles tests ---

    @Test
    void getPRFiles_Success_ReturnsFiles() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(connectedUser));
        when(tokenEncryptionService.decrypt(GITHUB_TOKEN)).thenReturn(GITHUB_TOKEN);

        Map<String, Object> file1 = Map.of(
                "filename", "src/Main.java",
                "status", "modified",
                "patch", "@@ -1,5 +1,6 @@\n+import java.util.*;"
        );

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map[].class)))
                .thenReturn(new ResponseEntity<>(new Map[]{file1}, HttpStatus.OK));

        List<Map<String, String>> result = githubService.getPRFiles(USER_ID, "owner", "repo", 1);

        assertEquals(1, result.size());
        assertEquals("src/Main.java", result.get(0).get("filename"));
        assertEquals("modified", result.get(0).get("status"));
    }

    // --- Helper ---

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}
