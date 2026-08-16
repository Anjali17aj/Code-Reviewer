package com.codereview.controller;

import com.codereview.dto.AuthRequest;
import com.codereview.dto.AuthResponse;
import com.codereview.dto.SignupRequest;
import com.codereview.dto.UserDTO;
import com.codereview.service.AuthService;
import com.codereview.service.JwtService;
import com.codereview.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final RateLimitService rateLimitService;

    private static final int LOGIN_RATE_LIMIT = 15;
    private static final int SIGNUP_RATE_LIMIT = 10;
    private static final int AUTH_RATE_WINDOW_MINUTES = 1;

    @PostMapping("/signup")
    public ResponseEntity<?> signup(
            @Valid @RequestBody SignupRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);

        // Rate limit: 3 attempts per minute per IP
        if (!rateLimitService.isIpAllowed("signup:" + clientIp, SIGNUP_RATE_LIMIT, AUTH_RATE_WINDOW_MINUTES)) {
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("timestamp", java.time.LocalDateTime.now().toString());
            errorBody.put("message", "Too many signup attempts. Please try again later.");
            errorBody.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(errorBody);
        }

        return ResponseEntity.ok(authService.signup(request));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody AuthRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);

        // Rate limit: 5 attempts per minute per IP
        if (!rateLimitService.isIpAllowed("login:" + clientIp, LOGIN_RATE_LIMIT, AUTH_RATE_WINDOW_MINUTES)) {
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("timestamp", java.time.LocalDateTime.now().toString());
            errorBody.put("message", "Too many login attempts. Please try again later.");
            errorBody.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(errorBody);
        }

        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null) {
            Map<String, Object> body = new HashMap<>();
            body.put("timestamp", java.time.LocalDateTime.now().toString());
            body.put("message", "Authentication required");
            body.put("status", HttpStatus.UNAUTHORIZED.value());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }
        Long userId = jwtService.extractUserId(token);
        return ResponseEntity.ok(authService.getUserById(userId));
    }

    /**
     * Extract client IP, considering X-Forwarded-For for proxied requests.
     */
    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // Take the first IP in the chain (original client)
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
