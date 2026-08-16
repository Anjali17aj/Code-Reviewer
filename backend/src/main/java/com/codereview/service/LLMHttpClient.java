package com.codereview.service;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * Interface for HTTP client operations to enable mocking in tests.
 */
public interface LLMHttpClient {

    ResponseEntity<String> exchange(
            String url,
            HttpMethod method,
            HttpEntity<String> requestEntity,
            Class<String> responseType
    );
}
