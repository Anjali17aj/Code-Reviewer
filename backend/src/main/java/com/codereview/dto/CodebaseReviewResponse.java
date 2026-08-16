package com.codereview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CodebaseReviewResponse extends ReviewResponse {

    private List<FileBreakdown> fileBreakdowns;
    private int totalFiles;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileBreakdown {
        private Long fileId;
        private String filePath;
        private String language;
        private int issueCount;
        private String assessment;
    }
}
