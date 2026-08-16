package com.codereview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GithubBranchDTO {
    private String name;

    @JsonProperty("default")
    private boolean defaultBranch;

    public boolean isDefaultBranch() {
        return defaultBranch;
    }
}
