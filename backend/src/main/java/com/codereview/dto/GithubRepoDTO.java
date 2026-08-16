package com.codereview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GithubRepoDTO {
    private Long id;
    private String name;

    @JsonProperty("full_name")
    private String fullName;

    private String description;

    @JsonProperty("html_url")
    private String htmlUrl;

    private String language;

    @JsonProperty("stargazers_count")
    private int stargazersCount;

    @JsonProperty("forks_count")
    private int forksCount;

    @JsonProperty("default_branch")
    private String defaultBranch;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("private")
    @lombok.Setter
    private boolean repoPrivate;

    public boolean isRepoPrivate() {
        return repoPrivate;
    }
}
