package com.codereview.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class LLMServiceException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String userMessage;

    public LLMServiceException(String message, Throwable cause, HttpStatus httpStatus, String userMessage) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.userMessage = userMessage;
    }

    public LLMServiceException(String message, HttpStatus httpStatus, String userMessage) {
        super(message);
        this.httpStatus = httpStatus;
        this.userMessage = userMessage;
    }
}
