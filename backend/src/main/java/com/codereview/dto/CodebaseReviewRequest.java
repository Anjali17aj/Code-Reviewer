package com.codereview.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodebaseReviewRequest {

    @NotEmpty(message = "File IDs list cannot be empty")
    private List<Long> fileIds;
}
