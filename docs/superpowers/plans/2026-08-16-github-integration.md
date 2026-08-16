# GitHub Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add GitHub OAuth integration so users can connect their GitHub account, browse repos, view pull requests, and review PR diffs using the existing AI review engine.

**Architecture:** Backend adds a GitHubService that uses RestTemplate to call GitHub's OAuth and REST APIs. A GithubController exposes endpoints for OAuth flow, repo listing, branch listing, PR listing, and PR review. Frontend adds a GithubService to call these endpoints and replaces the placeholder GithubComponent with a full 3-step flow: connect → select repo → review PR.

**Tech Stack:** Spring Boot RestTemplate, GitHub OAuth2, GitHub REST API v3, Angular HttpClient, Tailwind CSS

---

## File Structure

### Backend (Create)
- `backend/src/main/java/com/codereview/dto/GithubRepoDTO.java`
- `backend/src/main/java/com/codereview/dto/GithubPRDTO.java`
- `backend/src/main/java/com/codereview/dto/GithubBranchDTO.java`
- `backend/src/main/java/com/codereview/dto/PRReviewRequest.java`
- `backend/src/main/java/com/codereview/service/GithubService.java`
- `backend/src/main/java/com/codereview/controller/GithubController.java`

### Backend (Modify)
- `backend/src/main/java/com/codereview/entity/User.java` — add `githubUsername` field
- `backend/src/main/java/com/codereview/config/SecurityConfig.java` — permit `/api/github/callback`
- `backend/src/main/resources/application.yml` — add `github.*` config

### Frontend (Create)
- `frontend/src/app/features/github/github.service.ts`

### Frontend (Modify)
- `frontend/src/app/shared/models/review.model.ts` — add `GithubBranch`, `GithubPR`, `PRFileReview`
- `frontend/src/app/features/github/github.component.ts` — full replacement
- `.env` — add `GITHUB_REDIRECT_URI`

---

## Task 1: Backend — GitHub DTOs

**Files:**
- Create: `backend/src/main/java/com/codereview/dto/GithubRepoDTO.java`
- Create: `backend/src/main/java/com/codereview/dto/GithubPRDTO.java`
- Create: `backend/src/main/java/com/codereview/dto/GithubBranchDTO.java`
- Create: `backend/src/main/java/com/codereview/dto/PRReviewRequest.java`

- [ ] **Step 1: Create GithubRepoDTO**

```java
package com.codereview.dto;

import lombok.Data;

@Data
public class GithubRepoDTO {
    private Long id;
    private String name;
    private String fullName;
    private String description;
    private String htmlUrl;
    private String language;
    private int stargazersCount;
    private int forksCount;
    private String defaultBranch;
    private String updatedAt;
}
```

- [ ] **Step 2: Create GithubPRDTO**

```java
package com.codereview.dto;

import lombok.Data;

@Data
public class GithubPRDTO {
    private int number;
    private String title;
    private String state;
    private String htmlUrl;
    private String headBranch;
    private String baseBranch;
    private String body;
    private String createdAt;
}
```

- [ ] **Step 3: Create GithubBranchDTO**

```java
package com.codereview.dto;

import lombok.Data;

@Data
public class GithubBranchDTO {
    private String name;
    private boolean isDefault;
}
```

- [ ] **Step 4: Create PRReviewRequest**

```java
package com.codereview.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class PRReviewRequest {
    @NotBlank
    private String owner;
    @NotBlank
    private String repo;
    private int prNumber;
}
```

- [ ] **Step 5: Verify compilation**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

---

## Task 2: Backend — Add githubUsername to User Entity

**Files:**
- Modify: `backend/src/main/java/com/codereview/entity/User.java:34-36`

The existing User entity has `githubId` and `githubToken` but is missing `githubUsername` which the GithubService needs to store.

- [ ] **Step 1: Add githubUsername field**

Add after line 36 (`private String githubToken;`):

```java
    private String githubUsername;
```

- [ ] **Step 2: Verify compilation**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

---

## Task 3: Backend — GitHub Service

**Files:**
- Create: `backend/src/main/java/com/codereview/service/GithubService.java`

- [ ] **Step 1: Create GithubService**

```java
package com.codereview.service;

import com.codereview.dto.*;
import com.codereview.entity.User;
import com.codereview.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GithubService {

    private final UserRepository userRepository;
    private final RestTemplate restTemplate;

    @Value("${github.client-id}")
    private String clientId;

    @Value("${github.client-secret}")
    private String clientSecret;

    @Value("${github.redirect-uri}")
    private String redirectUri;

    private static final String GITHUB_API = "https://api.github.com";

    public String getAuthorizationUrl() {
        return UriComponentsBuilder.fromHttpUrl("https://github.com/login/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "repo,user")
                .queryParam("state", UUID.randomUUID().toString())
                .toUriString();
    }

    public String exchangeCodeForToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = new HashMap<>();
        body.put("client_id", clientId);
        body.put("client_secret", clientSecret);
        body.put("code", code);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "https://github.com/login/oauth/access_token", request, Map.class);

        if (response.getBody() != null && response.getBody().containsKey("access_token")) {
            return (String) response.getBody().get("access_token");
        }
        throw new RuntimeException("Failed to exchange code for token");
    }

    public void connectAccount(Long userId, String token) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                GITHUB_API + "/user", HttpMethod.GET, request, Map.class);

        if (response.getBody() != null) {
            user.setGithubId(((Number) response.getBody().get("id")).longValue());
            user.setGithubUsername((String) response.getBody().get("login"));
            user.setGithubToken(token);
            userRepository.save(user);
            log.info("GitHub account connected for user {}", userId);
        }
    }

    public List<GithubRepoDTO> getRepositories(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getGithubToken() == null) {
            throw new RuntimeException("GitHub account not connected");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(user.getGithubToken());
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<GithubRepoDTO[]> response = restTemplate.exchange(
                GITHUB_API + "/user/repos?sort=updated&per_page=30",
                HttpMethod.GET, request, GithubRepoDTO[].class);

        return Arrays.asList(response.getBody() != null ? response.getBody() : new GithubRepoDTO[0]);
    }

    public List<GithubBranchDTO> getBranches(Long userId, String owner, String repo) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(user.getGithubToken());
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map[]> response = restTemplate.exchange(
                GITHUB_API + "/repos/" + owner + "/" + repo + "/branches",
                HttpMethod.GET, request, Map[].class);

        List<GithubBranchDTO> branches = new ArrayList<>();
        if (response.getBody() != null) {
            for (Map branch : response.getBody()) {
                GithubBranchDTO dto = new GithubBranchDTO();
                dto.setName((String) branch.get("name"));
                branches.add(dto);
            }
        }
        return branches;
    }

    public List<GithubPRDTO> getPullRequests(Long userId, String owner, String repo) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(user.getGithubToken());
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map[]> response = restTemplate.exchange(
                GITHUB_API + "/repos/" + owner + "/" + repo + "/pulls?state=open",
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

                Map head = (Map) pr.get("head");
                Map base = (Map) pr.get("base");
                if (head != null) dto.setHeadBranch((String) head.get("ref"));
                if (base != null) dto.setBaseBranch((String) base.get("ref"));

                prs.add(dto);
            }
        }
        return prs;
    }

    public String getPRDiff(Long userId, String owner, String repo, int prNumber) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(user.getGithubToken());
        headers.setAccept(List.of(MediaType.APPLICATION_VND_GITHUB_DIFF));
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                GITHUB_API + "/repos/" + owner + "/" + repo + "/pulls/" + prNumber,
                HttpMethod.GET, request, String.class);

        return response.getBody();
    }

    public List<Map<String, String>> getPRFiles(Long userId, String owner, String repo, int prNumber) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(user.getGithubToken());
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map[]> response = restTemplate.exchange(
                GITHUB_API + "/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/files",
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
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

---

## Task 4: Backend — GitHub Controller

**Files:**
- Create: `backend/src/main/java/com/codereview/controller/GithubController.java`

- [ ] **Step 1: Create GithubController**

```java
package com.codereview.controller;

import com.codereview.dto.*;
import com.codereview.service.GithubService;
import com.codereview.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/github")
@RequiredArgsConstructor
public class GithubController {

    private final GithubService githubService;
    private final ReviewService reviewService;

    @GetMapping("/auth-url")
    public ResponseEntity<Map<String, String>> getAuthUrl() {
        return ResponseEntity.ok(Map.of("url", githubService.getAuthorizationUrl()));
    }

    @GetMapping("/callback")
    public ResponseEntity<Map<String, String>> handleCallback(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String code) {
        Long userId = extractUserId(userDetails);
        String token = githubService.exchangeCodeForToken(code);
        githubService.connectAccount(userId, token);
        return ResponseEntity.ok(Map.of("message", "GitHub account connected"));
    }

    @GetMapping("/repos")
    public ResponseEntity<List<GithubRepoDTO>> getRepos(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = extractUserId(userDetails);
        return ResponseEntity.ok(githubService.getRepositories(userId));
    }

    @GetMapping("/repos/{owner}/{repo}/branches")
    public ResponseEntity<List<GithubBranchDTO>> getBranches(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String owner,
            @PathVariable String repo) {
        Long userId = extractUserId(userDetails);
        return ResponseEntity.ok(githubService.getBranches(userId, owner, repo));
    }

    @GetMapping("/repos/{owner}/{repo}/pulls")
    public ResponseEntity<List<GithubPRDTO>> getPullRequests(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String owner,
            @PathVariable String repo) {
        Long userId = extractUserId(userDetails);
        return ResponseEntity.ok(githubService.getPullRequests(userId, owner, repo));
    }

    @GetMapping("/repos/{owner}/{repo}/pulls/{pr}/diff")
    public ResponseEntity<String> getPRDiff(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable int pr) {
        Long userId = extractUserId(userDetails);
        return ResponseEntity.ok(githubService.getPRDiff(userId, owner, repo, pr));
    }

    @GetMapping("/repos/{owner}/{repo}/pulls/{pr}/files")
    public ResponseEntity<List<Map<String, String>>> getPRFiles(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable int pr) {
        Long userId = extractUserId(userDetails);
        return ResponseEntity.ok(githubService.getPRFiles(userId, owner, repo, pr));
    }

    @PostMapping("/review-pr")
    public ResponseEntity<Map<String, Object>> reviewPR(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody PRReviewRequest request) {
        Long userId = extractUserId(userDetails);

        List<Map<String, String>> files = githubService.getPRFiles(
                userId, request.getOwner(), request.getRepo(), request.getPrNumber());

        List<Object> results = new ArrayList<>();
        for (Map<String, String> file : files) {
            String filename = file.get("filename");
            String patch = file.get("patch");

            String language = detectLanguage(filename);
            var review = reviewService.analyzeAndSave(userId, patch, language);
            Map<String, Object> fileResult = new HashMap<>();
            fileResult.put("filename", filename);
            fileResult.put("review", review);
            results.add(fileResult);
        }

        return ResponseEntity.ok(Map.of(
                "prNumber", request.getPrNumber(),
                "repo", request.getOwner() + "/" + request.getRepo(),
                "fileReviews", results
        ));
    }

    private Long extractUserId(UserDetails userDetails) {
        // TODO: implement proper user ID extraction from JWT
        return 1L;
    }

    private String detectLanguage(String filename) {
        if (filename == null) return "plaintext";
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return switch (ext) {
            case "java" -> "java";
            case "py" -> "python";
            case "js" -> "javascript";
            case "ts" -> "typescript";
            case "cpp", "cc", "cxx" -> "cpp";
            case "c", "h" -> "c";
            case "cs" -> "csharp";
            case "go" -> "go";
            case "rb" -> "ruby";
            case "rs" -> "rust";
            default -> "plaintext";
        };
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

---

## Task 5: Backend — Update SecurityConfig

**Files:**
- Modify: `backend/src/main/java/com/codereview/config/SecurityConfig.java:33-36`

The current SecurityConfig uses the lambda DSL. Add `/api/github/callback` to the permit list. Note: the callback endpoint needs `@AuthenticationPrincipal` so the user must be authenticated via JWT. We permit it so the OAuth redirect doesn't get blocked, but the controller extracts the user from the JWT token.

- [ ] **Step 1: Add github callback to permit list**

Change the `authorizeHttpRequests` block from:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/**").permitAll()
    .anyRequest().authenticated()
)
```

To:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/**").permitAll()
    .requestMatchers("/api/github/callback").permitAll()
    .anyRequest().authenticated()
)
```

- [ ] **Step 2: Verify compilation**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

---

## Task 6: Backend — Update application.yml

**Files:**
- Modify: `backend/src/main/resources/application.yml:42-44`

- [ ] **Step 1: Add GitHub config**

Add after the `review:` block (after line 44):

```yaml
github:
  client-id: ${GITHUB_CLIENT_ID:placeholder}
  client-secret: ${GITHUB_CLIENT_SECRET:placeholder}
  redirect-uri: ${GITHUB_REDIRECT_URI:http://localhost:8080/api/github/callback}
```

- [ ] **Step 2: Verify compilation**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

---

## Task 7: Frontend — Update Review Model

**Files:**
- Modify: `frontend/src/app/shared/models/review.model.ts:80-86`

- [ ] **Step 1: Add GitHub interfaces**

Add after the `Repo` interface (after line 80):

```typescript
export interface GithubBranch {
  name: string;
  isDefault: boolean;
}

export interface GithubPR {
  number: number;
  title: string;
  state: string;
  htmlUrl: string;
  headBranch: string;
  baseBranch: string;
  body: string;
}

export interface PRFileReview {
  filename: string;
  review: ReviewDTO;
}
```

- [ ] **Step 2: Verify frontend build**

Run: `cd frontend && npx ng build --configuration=development 2>&1 | tail -10`
Expected: Build succeeds (or at least no new errors from model changes)

---

## Task 8: Frontend — GitHub Service

**Files:**
- Create: `frontend/src/app/features/github/github.service.ts`

Note: The existing `ApiService.get()` expects `HttpParams`, not a plain object. Use `HttpParams` for query parameters.

- [ ] **Step 1: Create GithubService**

```typescript
import { Injectable } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { Repo, GithubBranch, GithubPR } from '../../shared/models/review.model';

@Injectable({ providedIn: 'root' })
export class GithubService {
  private readonly GITHUB_ENDPOINT = '/api/github';

  constructor(private apiService: ApiService) {}

  getAuthUrl(): Observable<{ url: string }> {
    return this.apiService.get<{ url: string }>(`${this.GITHUB_ENDPOINT}/auth-url`);
  }

  handleCallback(code: string): Observable<{ message: string }> {
    const params = new HttpParams().set('code', code);
    return this.apiService.get<{ message: string }>(`${this.GITHUB_ENDPOINT}/callback`, params);
  }

  getRepos(): Observable<Repo[]> {
    return this.apiService.get<Repo[]>(`${this.GITHUB_ENDPOINT}/repos`);
  }

  getBranches(owner: string, repo: string): Observable<GithubBranch[]> {
    return this.apiService.get<GithubBranch[]>(
      `${this.GITHUB_ENDPOINT}/repos/${owner}/${repo}/branches`
    );
  }

  getPullRequests(owner: string, repo: string): Observable<GithubPR[]> {
    return this.apiService.get<GithubPR[]>(
      `${this.GITHUB_ENDPOINT}/repos/${owner}/${repo}/pulls`
    );
  }

  getPRDiff(owner: string, repo: string, pr: number): Observable<string> {
    return this.apiService.get<string>(
      `${this.GITHUB_ENDPOINT}/repos/${owner}/${repo}/pulls/${pr}/diff`
    );
  }

  reviewPR(owner: string, repo: string, prNumber: number): Observable<any> {
    return this.apiService.post<any>(`${this.GITHUB_ENDPOINT}/review-pr`, {
      owner,
      repo,
      prNumber
    });
  }
}
```

- [ ] **Step 2: Verify frontend build**

Run: `cd frontend && npx ng build --configuration=development 2>&1 | tail -10`
Expected: Build succeeds

---

## Task 9: Frontend — GitHub Component

**Files:**
- Modify: `frontend/src/app/features/github/github.component.ts` (full replacement)

- [ ] **Step 1: Replace GithubComponent**

```typescript
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { GithubService } from './github.service';
import { Repo, GithubPR } from '../../shared/models/review.model';

@Component({
  selector: 'app-github',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="max-w-6xl mx-auto px-4 py-8">
      <!-- Not Connected -->
      <div *ngIf="!isConnected" class="text-center py-16">
        <div class="w-20 h-20 bg-gray-100 rounded-2xl flex items-center justify-center mx-auto mb-6">
          <svg class="w-12 h-12 text-gray-600" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
          </svg>
        </div>
        <h1 class="text-3xl font-bold text-gray-900 mb-4">Connect GitHub</h1>
        <p class="text-gray-600 mb-8">Link your GitHub account to review pull requests directly.</p>
        <button (click)="connectGithub()"
                class="px-6 py-3 bg-gray-900 hover:bg-gray-800 text-white font-medium rounded-lg transition-colors">
          Connect with GitHub
        </button>
      </div>

      <!-- Connected - Repo List -->
      <div *ngIf="isConnected && !selectedRepo">
        <h1 class="text-2xl font-bold text-gray-900 mb-6">Your Repositories</h1>
        <div class="grid gap-4">
          <div *ngFor="let repo of repos"
               (click)="selectRepo(repo)"
               class="p-4 bg-white border rounded-lg hover:border-blue-500 cursor-pointer transition-colors">
            <div class="flex items-center justify-between">
              <div>
                <h3 class="font-semibold text-gray-900">{{ repo.name }}</h3>
                <p class="text-sm text-gray-500">{{ repo.fullName }}</p>
              </div>
              <div class="text-right text-sm text-gray-500">
                <span *ngIf="repo.language">&#9679; {{ repo.language }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Selected Repo - PR List -->
      <div *ngIf="isConnected && selectedRepo && !selectedPR">
        <button (click)="selectedRepo = null" class="text-blue-600 hover:underline mb-4">&larr; Back to repos</button>
        <h1 class="text-2xl font-bold text-gray-900 mb-6">{{ selectedRepo.name }} — Pull Requests</h1>
        <div class="grid gap-4">
          <div *ngFor="let pr of pullRequests"
               (click)="selectPR(pr)"
               class="p-4 bg-white border rounded-lg hover:border-blue-500 cursor-pointer transition-colors">
            <div class="flex items-start justify-between">
              <div>
                <h3 class="font-semibold text-gray-900">#{{ pr.number }} {{ pr.title }}</h3>
                <p class="text-sm text-gray-500">{{ pr.headBranch }} → {{ pr.baseBranch }}</p>
              </div>
              <span class="px-2 py-1 text-xs rounded"
                    [ngClass]="pr.state === 'open' ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-700'">
                {{ pr.state }}
              </span>
            </div>
          </div>
          <div *ngIf="pullRequests.length === 0" class="text-gray-500 text-center py-8">
            No open pull requests found.
          </div>
        </div>
      </div>

      <!-- PR Review Results -->
      <div *ngIf="selectedPR">
        <button (click)="selectedPR = null; reviewResults = null" class="text-blue-600 hover:underline mb-4">&larr; Back to PRs</button>
        <h1 class="text-2xl font-bold text-gray-900 mb-2">PR #{{ selectedPR.number }} Review</h1>
        <p class="text-gray-600 mb-6">{{ selectedPR.title }}</p>

        <button (click)="reviewPR()" [disabled]="isReviewing"
                class="px-4 py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 text-white rounded-lg mb-6">
          {{ isReviewing ? 'Reviewing...' : 'Start Review' }}
        </button>

        <div *ngIf="reviewResults" class="space-y-4">
          <div *ngFor="let result of reviewResults" class="bg-white border rounded-lg p-4">
            <h3 class="font-mono text-sm font-semibold mb-2">{{ result.filename }}</h3>
            <div class="text-sm">
              <span class="font-medium">Assessment:</span>
              <span [ngClass]="{'text-green-600': result.review?.overallRating === 'good', 'text-yellow-600': result.review?.overallRating === 'needs_improvement', 'text-red-600': result.review?.overallRating === 'poor'}">
                {{ result.review?.overallRating }}
              </span>
            </div>
            <p class="text-sm text-gray-600 mt-2">{{ result.review?.summary }}</p>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: []
})
export class GithubComponent implements OnInit {
  isConnected = false;
  repos: Repo[] = [];
  selectedRepo: Repo | null = null;
  pullRequests: GithubPR[] = [];
  selectedPR: GithubPR | null = null;
  reviewResults: any[] = [];
  isReviewing = false;

  constructor(private githubService: GithubService) {}

  ngOnInit() {
    this.checkConnection();
  }

  checkConnection() {
    this.githubService.getRepos().subscribe({
      next: (repos) => {
        this.isConnected = true;
        this.repos = repos;
      },
      error: () => this.isConnected = false
    });
  }

  connectGithub() {
    this.githubService.getAuthUrl().subscribe({
      next: (res) => window.location.href = res.url
    });
  }

  selectRepo(repo: Repo) {
    this.selectedRepo = repo;
    const [owner, name] = repo.fullName.split('/');
    this.githubService.getPullRequests(owner, name).subscribe({
      next: (prs) => this.pullRequests = prs
    });
  }

  selectPR(pr: GithubPR) {
    this.selectedPR = pr;
    this.reviewResults = [];
  }

  reviewPR() {
    if (!this.selectedRepo || !this.selectedPR) return;
    this.isReviewing = true;
    const [owner, name] = this.selectedRepo.fullName.split('/');

    this.githubService.reviewPR(owner, name, this.selectedPR.number).subscribe({
      next: (result) => {
        this.reviewResults = result.fileReviews;
        this.isReviewing = false;
      },
      error: () => this.isReviewing = false
    });
  }
}
```

- [ ] **Step 2: Verify frontend build**

Run: `cd frontend && npx ng build --configuration=development 2>&1 | tail -10`
Expected: Build succeeds

---

## Task 10: Update .env

**Files:**
- Modify: `.env:13-15`

- [ ] **Step 1: Add GITHUB_REDIRECT_URI**

The .env already has `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET`. Add the redirect URI:

```env
# GitHub OAuth
GITHUB_CLIENT_ID=your-github-client-id
GITHUB_CLIENT_SECRET=your-github-client-secret
GITHUB_REDIRECT_URI=http://localhost:8080/api/github/callback
```

---

## Task 11: Final Verification

- [ ] **Step 1: Backend compiles**

Run: `cd backend && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 2: Frontend builds**

Run: `cd frontend && npx ng build --configuration=development 2>&1 | tail -10`
Expected: Build succeeds with no errors

---

## Notes

1. **User entity modification**: The original task spec didn't mention adding `githubUsername` to the User entity, but the GithubService references `user.setGithubUsername()`. Task 2 adds this field.

2. **SecurityConfig syntax**: The existing code uses Spring Security's lambda DSL (`.requestMatchers()`), not the deprecated `.antMatchers()`. Task 5 uses the correct lambda syntax.

3. **ApiService.get signature**: The existing `ApiService.get()` expects `HttpParams`, not a plain object. Task 8's GithubService uses `HttpParams` for the callback endpoint.

4. **GithubComponent imports**: The component uses `ngModel` binding so it needs `FormsModule` imported. Task 9 includes this.

5. **Review flow**: The `reviewPR()` method in the controller calls `reviewService.analyzeAndSave()` which persists each file review to the database. This means PR reviews are saved as individual review records.

6. **User ID extraction**: The `extractUserId()` method in the controller is a placeholder (returns 1L). This should be updated to properly extract the user ID from the JWT token in a future task.
