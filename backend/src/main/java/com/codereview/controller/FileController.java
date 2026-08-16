package com.codereview.controller;

import com.codereview.dto.CodeFileContent;
import com.codereview.dto.CodeFileDTO;
import com.codereview.dto.FolderDTO;
import com.codereview.service.FileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    // Folder endpoints
    @PostMapping("/folders")
    public ResponseEntity<FolderDTO> createFolder(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateFolderRequest request) {
        Long userId = extractUserId(userDetails);
        return ResponseEntity.ok(fileService.createFolder(userId, request.getName(), request.getParentId()));
    }

    @GetMapping("/folders")
    public ResponseEntity<List<FolderDTO>> getFolders(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Long parentId) {
        Long userId = extractUserId(userDetails);
        return ResponseEntity.ok(fileService.getFolders(userId, parentId));
    }

    @PutMapping("/folders/{id}")
    public ResponseEntity<FolderDTO> renameFolder(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody RenameFolderRequest request) {
        Long userId = extractUserId(userDetails);
        return ResponseEntity.ok(fileService.renameFolder(userId, id, request.getName()));
    }

    @DeleteMapping("/folders/{id}")
    public ResponseEntity<Void> deleteFolder(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        Long userId = extractUserId(userDetails);
        fileService.deleteFolder(userId, id);
        return ResponseEntity.noContent().build();
    }

    // File endpoints
    @PostMapping
    public ResponseEntity<CodeFileDTO> createFile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateFileRequest request) {
        Long userId = extractUserId(userDetails);
        return ResponseEntity.ok(fileService.createFile(userId, request.getName(), request.getLanguage(), request.getContent(), request.getFolderId()));
    }

    @PostMapping("/upload")
    public ResponseEntity<CodeFileDTO> uploadFile(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long folderId) throws IOException {
        Long userId = extractUserId(userDetails);
        return ResponseEntity.ok(fileService.uploadFile(userId, file, folderId));
    }

    @GetMapping
    public ResponseEntity<List<CodeFileDTO>> getFiles(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Long folderId) {
        Long userId = extractUserId(userDetails);
        return ResponseEntity.ok(fileService.getFiles(userId, folderId));
    }

    @GetMapping("/tree")
    public ResponseEntity<List<Object>> getFileTree(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = extractUserId(userDetails);
        return ResponseEntity.ok(fileService.getFileTree(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CodeFileDTO> getFile(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        Long userId = extractUserId(userDetails);
        return ResponseEntity.ok(fileService.getFile(userId, id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CodeFileDTO> updateFile(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody UpdateFileRequest request) {
        Long userId = extractUserId(userDetails);
        return ResponseEntity.ok(fileService.updateFile(userId, id, request.getName(), request.getContent()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        Long userId = extractUserId(userDetails);
        fileService.deleteFile(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/move")
    public ResponseEntity<CodeFileDTO> moveFile(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestBody MoveFileRequest request) {
        Long userId = extractUserId(userDetails);
        return ResponseEntity.ok(fileService.moveFile(userId, id, request.getTargetFolderId()));
    }

    // Bulk endpoints
    @PostMapping("/bulk")
    public ResponseEntity<List<CodeFileDTO>> bulkCreateFiles(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody BulkCreateFilesRequest request) {
        Long userId = extractUserId(userDetails);
        List<CodeFileDTO> result = fileService.createBulkFiles(userId, request.getFiles(), request.getFolderId());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/upload-multiple")
    public ResponseEntity<List<CodeFileDTO>> uploadMultipleFiles(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(required = false) Long folderId) throws IOException {
        Long userId = extractUserId(userDetails);
        List<CodeFileDTO> result = fileService.uploadMultipleFiles(userId, files, folderId);
        return ResponseEntity.ok(result);
    }

    private Long extractUserId(UserDetails userDetails) {
        if (userDetails instanceof com.codereview.service.JwtUserDetails jwtUser) {
            return jwtUser.getUserId();
        }
        throw new RuntimeException("Invalid authentication");
    }

    // Request DTOs
    @Data
    public static class CreateFolderRequest {
        @NotBlank
        private String name;
        private Long parentId;
    }

    @Data
    public static class RenameFolderRequest {
        @NotBlank
        private String name;
    }

    @Data
    public static class CreateFileRequest {
        @NotBlank
        private String name;
        @NotBlank
        private String language;
        private String content;
        private Long folderId;
    }

    @Data
    public static class UpdateFileRequest {
        private String name;
        private String content;
    }

    @Data
    public static class MoveFileRequest {
        private Long targetFolderId;
    }

    @Data
    public static class BulkCreateFilesRequest {
        private List<CodeFileContent> files;
        private Long folderId;
    }
}
