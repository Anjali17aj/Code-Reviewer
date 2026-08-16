package com.codereview.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CodeFileDTO {
    private Long id;
    private Long userId;
    private String name;
    private String language;
    private String content;
    private Long folderId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
