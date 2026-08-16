package com.codereview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GithubPRDTO {
    private int number;
    private String title;
    private String state;

    @JsonProperty("html_url")
    private String htmlUrl;

    private String headBranch;
    private String baseBranch;
    private String body;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("user")
    private GithubUserDTO user;

    @Data
    public static class GithubUserDTO {
        private String login;
    }
}
