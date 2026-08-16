package com.codereview.service;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Implementation of LLMHttpClient that wraps Spring's RestTemplate.
 */
@Component
public class RestTemplateLLMHttpClient implements LLMHttpClient {

    private final RestTemplate restTemplate;

    public RestTemplateLLMHttpClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public ResponseEntity<String> exchange(
            String url,
            HttpMethod method,
            HttpEntity<String> requestEntity,
            Class<String> responseType
    ) {
        return restTemplate.exchange(url, method, requestEntity, responseType);
    }
}
