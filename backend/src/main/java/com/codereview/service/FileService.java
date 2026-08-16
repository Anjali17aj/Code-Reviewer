package com.codereview.service;

import com.codereview.dto.CodeFileContent;
import com.codereview.dto.CodeFileDTO;
import com.codereview.dto.FolderDTO;
import com.codereview.entity.CodeFile;
import com.codereview.entity.Folder;
import com.codereview.repository.CodeFileRepository;
import com.codereview.repository.FolderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private static final long MAX_FILE_SIZE = 1024 * 1024; // 1MB per file
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "java", "py", "js", "ts", "jsx", "tsx", "cpp", "cc", "cxx", "c", "h",
        "cs", "go", "rb", "rs", "kt", "swift", "php", "txt", "md", "json",
        "xml", "yml", "yaml", "sql", "sh", "bash", "html", "css", "scss"
    );
    private static final java.util.regex.Pattern FOLDER_NAME_PATTERN =
        java.util.regex.Pattern.compile("^[a-zA-Z0-9_\\-. ]+$");

    private final CodeFileRepository codeFileRepository;
    private final FolderRepository folderRepository;

    // --- Folder operations ---

    public FolderDTO createFolder(Long userId, String name, Long parentId) {
        if (name == null || name.isBlank()) {
            throw new RuntimeException("Folder name is required");
        }
        if (name.length() > 100) {
            throw new RuntimeException("Folder name too long (max 100 characters)");
        }
        if (!FOLDER_NAME_PATTERN.matcher(name).matches()) {
            throw new RuntimeException("Folder name contains invalid characters");
        }

        // Validate parent exists if specified
        if (parentId != null) {
            folderRepository.findById(parentId)
                    .filter(f -> f.getUserId().equals(userId))
                    .orElseThrow(() -> new RuntimeException("Parent folder not found"));
        }

        Folder folder = new Folder();
        folder.setUserId(userId);
        folder.setName(name);
        folder.setParentId(parentId);
        folder.setCreatedAt(LocalDateTime.now());
        Folder saved = folderRepository.save(folder);
        return mapFolderToDTO(saved);
    }

    public List<FolderDTO> getFolders(Long userId, Long parentId) {
        List<Folder> folders;
        if (parentId == null) {
            folders = folderRepository.findByUserIdAndParentIdIsNullOrderByCreatedAtDesc(userId);
        } else {
            folders = folderRepository.findByUserIdAndParentIdOrderByCreatedAtDesc(userId, parentId);
        }
        return folders.stream().map(this::mapFolderToDTO).collect(Collectors.toList());
    }

    public FolderDTO renameFolder(Long userId, Long folderId, String newName) {
        if (newName == null || newName.isBlank()) {
            throw new RuntimeException("Folder name is required");
        }
        if (newName.length() > 100) {
            throw new RuntimeException("Folder name too long (max 100 characters)");
        }
        if (!FOLDER_NAME_PATTERN.matcher(newName).matches()) {
            throw new RuntimeException("Folder name contains invalid characters");
        }

        Folder folder = folderRepository.findById(folderId)
                .filter(f -> f.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Folder not found"));
        folder.setName(newName);
        Folder saved = folderRepository.save(folder);
        return mapFolderToDTO(saved);
    }

    @Transactional
    public void deleteFolder(Long userId, Long folderId) {
        Folder folder = folderRepository.findById(folderId)
                .filter(f -> f.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Folder not found"));
        // Delete all files in folder
        codeFileRepository.deleteByUserIdAndFolderId(userId, folderId);
        // Delete subfolders recursively
        deleteSubfolders(userId, folderId);
        // Delete the folder itself
        folderRepository.delete(folder);
    }

    private void deleteSubfolders(Long userId, Long parentId) {
        List<Folder> subfolders = folderRepository.findByUserIdAndParentIdOrderByCreatedAtDesc(userId, parentId);
        for (Folder subfolder : subfolders) {
            codeFileRepository.deleteByUserIdAndFolderId(userId, subfolder.getId());
            deleteSubfolders(userId, subfolder.getId());
            folderRepository.delete(subfolder);
        }
    }

    // --- File operations ---

    public CodeFileDTO createFile(Long userId, String name, String language, String content, Long folderId) {
        if (name == null || name.isBlank()) {
            throw new RuntimeException("File name is required");
        }
        if (content == null) {
            content = "";
        }
        if (language == null || language.isBlank()) {
            language = detectLanguage(name);
        }

        // Validate folder exists if specified
        if (folderId != null) {
            folderRepository.findById(folderId)
                    .filter(f -> f.getUserId().equals(userId))
                    .orElseThrow(() -> new RuntimeException("Folder not found"));
        }

        CodeFile file = new CodeFile();
        file.setUserId(userId);
        file.setName(name);
        file.setLanguage(language);
        file.setContent(content);
        file.setFolderId(folderId);
        file.setCreatedAt(LocalDateTime.now());
        file.setUpdatedAt(LocalDateTime.now());
        CodeFile saved = codeFileRepository.save(file);
        return mapFileToDTO(saved);
    }

    public CodeFileDTO uploadFile(Long userId, MultipartFile multipartFile, Long folderId) throws IOException {
        // Validate file size
        if (multipartFile.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("File size exceeds maximum limit of 1MB: "
                    + multipartFile.getOriginalFilename());
        }

        String filename = multipartFile.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new RuntimeException("Invalid filename");
        }

        // Validate file extension
        String ext = getExtension(filename);
        if (ext.isEmpty()) {
            throw new RuntimeException("File must have an extension");
        }
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new RuntimeException("File type not allowed ('" + ext + "'). Allowed: "
                    + String.join(", ", ALLOWED_EXTENSIONS));
        }

        // Sanitize filename
        filename = sanitizeFilename(filename);

        String content = new String(multipartFile.getBytes(), StandardCharsets.UTF_8);
        String language = detectLanguage(filename);
        return createFile(userId, filename, language, content, folderId);
    }

    public List<CodeFileDTO> getFiles(Long userId, Long folderId) {
        List<CodeFile> files;
        if (folderId == null) {
            files = codeFileRepository.findByUserIdAndFolderIdIsNullOrderByUpdatedAtDesc(userId);
        } else {
            files = codeFileRepository.findByUserIdAndFolderIdOrderByUpdatedAtDesc(userId, folderId);
        }
        return files.stream().map(this::mapFileToDTO).collect(Collectors.toList());
    }

    public CodeFileDTO getFile(Long userId, Long fileId) {
        CodeFile file = codeFileRepository.findById(fileId)
                .filter(f -> f.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("File not found"));
        return mapFileToDTO(file);
    }

    public CodeFileDTO updateFile(Long userId, Long fileId, String name, String content) {
        CodeFile file = codeFileRepository.findById(fileId)
                .filter(f -> f.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("File not found"));
        if (name != null && !name.isBlank()) file.setName(name);
        if (content != null) {
            file.setContent(content);
            file.setUpdatedAt(LocalDateTime.now());
        }
        CodeFile saved = codeFileRepository.save(file);
        return mapFileToDTO(saved);
    }

    public void deleteFile(Long userId, Long fileId) {
        CodeFile file = codeFileRepository.findById(fileId)
                .filter(f -> f.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("File not found"));
        codeFileRepository.delete(file);
    }

    public CodeFileDTO moveFile(Long userId, Long fileId, Long targetFolderId) {
        CodeFile file = codeFileRepository.findById(fileId)
                .filter(f -> f.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("File not found"));

        // Validate target folder exists if specified
        if (targetFolderId != null) {
            folderRepository.findById(targetFolderId)
                    .filter(f -> f.getUserId().equals(userId))
                    .orElseThrow(() -> new RuntimeException("Target folder not found"));
        }

        file.setFolderId(targetFolderId);
        file.setUpdatedAt(LocalDateTime.now());
        CodeFile saved = codeFileRepository.save(file);
        return mapFileToDTO(saved);
    }

    // --- Bulk operations ---

    public List<CodeFileDTO> createBulkFiles(Long userId, List<CodeFileContent> files, Long folderId) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        List<CodeFileDTO> result = new java.util.ArrayList<>();
        for (CodeFileContent fileContent : files) {
            try {
                CodeFileDTO dto = createFile(userId, fileContent.getName(), fileContent.getLanguage(),
                        fileContent.getContent(), folderId);
                result.add(dto);
            } catch (Exception e) {
                log.error("Failed to create file {}: {}", fileContent.getName(), e.getMessage());
                // Skip failed files, continue with others
            }
        }
        return result;
    }

    /**
     * Upload multiple files with per-file error handling.
     * Returns results for all files that succeeded, logs errors for failures.
     */
    public List<CodeFileDTO> uploadMultipleFiles(Long userId, List<MultipartFile> multipartFiles, Long folderId)
            throws IOException {
        if (multipartFiles == null || multipartFiles.isEmpty()) {
            return List.of();
        }

        List<CodeFileDTO> result = new java.util.ArrayList<>();
        List<String> errors = new java.util.ArrayList<>();

        for (MultipartFile multipartFile : multipartFiles) {
            try {
                CodeFileDTO dto = uploadFile(userId, multipartFile, folderId);
                result.add(dto);
            } catch (Exception e) {
                String filename = multipartFile.getOriginalFilename() != null
                        ? multipartFile.getOriginalFilename() : "unknown";
                String error = filename + ": " + e.getMessage();
                errors.add(error);
                log.warn("Failed to upload file {}: {}", filename, e.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            log.warn("Upload errors for user {}: {}", userId, errors);
        }

        return result;
    }

    // --- Tree view ---

    public List<Object> getFileTree(Long userId) {
        List<Folder> rootFolders = folderRepository.findByUserIdAndParentIdIsNullOrderByCreatedAtDesc(userId);
        List<CodeFile> rootFiles = codeFileRepository.findByUserIdAndFolderIdIsNullOrderByUpdatedAtDesc(userId);

        java.util.List<Object> tree = new java.util.ArrayList<>();
        for (Folder folder : rootFolders) {
            tree.add(buildFolderNode(userId, folder));
        }
        for (CodeFile file : rootFiles) {
            tree.add(mapFileToDTO(file));
        }
        return tree;
    }

    private java.util.Map<String, Object> buildFolderNode(Long userId, Folder folder) {
        java.util.Map<String, Object> node = new java.util.HashMap<>();
        node.put("type", "folder");
        node.put("id", folder.getId());
        node.put("name", folder.getName());
        node.put("createdAt", folder.getCreatedAt());

        // Get children
        List<Folder> subfolders = folderRepository.findByUserIdAndParentIdOrderByCreatedAtDesc(userId, folder.getId());
        List<CodeFile> files = codeFileRepository.findByUserIdAndFolderIdOrderByUpdatedAtDesc(userId, folder.getId());

        java.util.List<Object> children = new java.util.ArrayList<>();
        for (Folder subfolder : subfolders) {
            children.add(buildFolderNode(userId, subfolder));
        }
        for (CodeFile file : files) {
            children.add(mapFileToDTO(file));
        }
        node.put("children", children);
        return node;
    }

    // --- Utility methods ---

    private String detectLanguage(String filename) {
        if (filename == null) return "plaintext";
        String ext = getExtension(filename);
        if (ext.isEmpty()) return "plaintext";
        return switch (ext) {
            case "java", "kt" -> "java";
            case "py" -> "python";
            case "js", "jsx" -> "javascript";
            case "ts", "tsx" -> "typescript";
            case "cpp", "cc", "cxx" -> "cpp";
            case "c", "h" -> "c";
            case "cs" -> "csharp";
            case "go" -> "go";
            case "rb" -> "ruby";
            case "rs" -> "rust";
            case "php" -> "php";
            default -> "plaintext";
        };
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase();
    }

    /**
     * Sanitize a filename by replacing dangerous characters.
     */
    private String sanitizeFilename(String filename) {
        // Remove path separators and null bytes
        String sanitized = filename.replaceAll("[/\\\\\\x00]", "_");
        // Replace other dangerous characters
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        // Prevent double dots (path traversal)
        sanitized = sanitized.replaceAll("\\.\\.", "_");
        // Limit length
        if (sanitized.length() > 255) {
            String ext = getExtension(sanitized);
            sanitized = sanitized.substring(0, 255 - ext.length() - 1) + "." + ext;
        }
        return sanitized;
    }

    private CodeFileDTO mapFileToDTO(CodeFile file) {
        CodeFileDTO dto = new CodeFileDTO();
        dto.setId(file.getId());
        dto.setUserId(file.getUserId());
        dto.setName(file.getName());
        dto.setLanguage(file.getLanguage());
        dto.setContent(file.getContent());
        dto.setFolderId(file.getFolderId());
        dto.setCreatedAt(file.getCreatedAt());
        dto.setUpdatedAt(file.getUpdatedAt());
        return dto;
    }

    private FolderDTO mapFolderToDTO(Folder folder) {
        FolderDTO dto = new FolderDTO();
        dto.setId(folder.getId());
        dto.setUserId(folder.getUserId());
        dto.setName(folder.getName());
        dto.setParentId(folder.getParentId());
        dto.setCreatedAt(folder.getCreatedAt());
        return dto;
    }
}
