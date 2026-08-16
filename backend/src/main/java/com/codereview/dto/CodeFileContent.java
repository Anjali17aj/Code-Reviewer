package com.codereview.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeFileContent {

    @NotBlank(message = "File name is required")
    private String name;

    private String language;

    @NotBlank(message = "File content is required")
    private String content;
}
