package com.codereview.dto;

import lombok.Data;

@Data
public class GithubCallbackRequest {
    private String code;
    private String state;
}
