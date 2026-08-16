package com.codereview.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FolderDTO {
    private Long id;
    private Long userId;
    private String name;
    private Long parentId;
    private LocalDateTime createdAt;
}
