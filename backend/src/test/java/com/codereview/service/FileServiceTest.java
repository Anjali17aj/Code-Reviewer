package com.codereview.service;

import com.codereview.dto.CodeFileDTO;
import com.codereview.entity.CodeFile;
import com.codereview.repository.CodeFileRepository;
import com.codereview.repository.FolderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private CodeFileRepository codeFileRepository;

    @Mock
    private FolderRepository folderRepository;

    @InjectMocks
    private FileService fileService;

    private static final Long USER_ID = 1L;

    // --- createBulkFiles tests ---

    @Test
    void createBulkFiles_CreatesMultipleFiles() {
        when(codeFileRepository.save(any(CodeFile.class))).thenAnswer(invocation -> {
            CodeFile f = invocation.getArgument(0);
            f.setId(System.nanoTime());
            return f;
        });

        var files = List.of(
                new com.codereview.dto.CodeFileContent("Foo.java", "java", "class Foo { }"),
                new com.codereview.dto.CodeFileContent("Bar.java", "python", "class Bar: pass")
        );

        List<CodeFileDTO> result = fileService.createBulkFiles(USER_ID, files, null);

        assertEquals(2, result.size());
        assertEquals("Foo.java", result.get(0).getName());
        assertEquals("Bar.java", result.get(1).getName());
        verify(codeFileRepository, times(2)).save(any(CodeFile.class));
    }

    @Test
    void createBulkFiles_EmptyList_ReturnsEmptyList() {
        List<CodeFileDTO> result = fileService.createBulkFiles(USER_ID, List.of(), null);
        assertTrue(result.isEmpty());
        verify(codeFileRepository, never()).save(any());
    }

    @Test
    void createBulkFiles_SetsUserIdOnAllFiles() {
        when(codeFileRepository.save(any(CodeFile.class))).thenAnswer(invocation -> {
            CodeFile f = invocation.getArgument(0);
            f.setId(1L);
            return f;
        });

        var files = List.of(
                new com.codereview.dto.CodeFileContent("Foo.java", "java", "class Foo { }")
        );

        fileService.createBulkFiles(USER_ID, files, null);

        verify(codeFileRepository).save(argThat(f -> f.getUserId().equals(USER_ID)));
    }

    // --- uploadMultipleFiles tests ---

    @Test
    void uploadMultipleFiles_UploadsMultipleMultipartFiles() throws IOException {
        when(codeFileRepository.save(any(CodeFile.class))).thenAnswer(invocation -> {
            CodeFile f = invocation.getArgument(0);
            f.setId(System.nanoTime());
            return f;
        });

        MockMultipartFile file1 = new MockMultipartFile(
                "files", "Foo.java", "text/plain",
                "class Foo { }".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file2 = new MockMultipartFile(
                "files", "Bar.java", "text/plain",
                "class Bar { }".getBytes(StandardCharsets.UTF_8));

        List<CodeFileDTO> result = fileService.uploadMultipleFiles(USER_ID, List.of(file1, file2), null);

        assertEquals(2, result.size());
        verify(codeFileRepository, times(2)).save(any(CodeFile.class));
    }

    @Test
    void uploadMultipleFiles_EmptyList_ReturnsEmptyList() throws IOException {
        List<CodeFileDTO> result = fileService.uploadMultipleFiles(USER_ID, List.of(), null);
        assertTrue(result.isEmpty());
    }

    @Test
    void uploadMultipleFiles_FileTooLarge_SkipsFileAndReturnsEmpty() throws IOException {
        // Create a file that exceeds 1MB
        byte[] largeContent = new byte[1024 * 1024 + 1];
        MockMultipartFile largeFile = new MockMultipartFile(
                "files", "Big.java", "text/plain", largeContent);

        // Multi-upload now catches per-file errors, so it returns empty list
        List<CodeFileDTO> result = fileService.uploadMultipleFiles(USER_ID, List.of(largeFile), null);

        assertTrue(result.isEmpty());
        verify(codeFileRepository, never()).save(any());
    }

    @Test
    void uploadMultipleFiles_InvalidExtension_SkipsFileAndReturnsEmpty() {
        MockMultipartFile exeFile = new MockMultipartFile(
                "files", "malware.exe", "text/plain",
                "bad stuff".getBytes(StandardCharsets.UTF_8));

        // Multi-upload now catches per-file errors, so it returns empty list
        List<CodeFileDTO> result;
        try {
            result = fileService.uploadMultipleFiles(USER_ID, List.of(exeFile), null);
        } catch (IOException e) {
            fail("Should not throw IOException");
            return;
        }

        assertTrue(result.isEmpty());
        verify(codeFileRepository, never()).save(any());
    }

    @Test
    void uploadMultipleFiles_MixedValidAndInvalid_ReturnsOnlyValid() throws IOException {
        when(codeFileRepository.save(any(CodeFile.class))).thenAnswer(invocation -> {
            CodeFile f = invocation.getArgument(0);
            f.setId(System.nanoTime());
            return f;
        });

        MockMultipartFile validFile = new MockMultipartFile(
                "files", "Good.java", "text/plain",
                "class Good { }".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile invalidFile = new MockMultipartFile(
                "files", "malware.exe", "text/plain",
                "bad stuff".getBytes(StandardCharsets.UTF_8));

        List<CodeFileDTO> result = fileService.uploadMultipleFiles(USER_ID, List.of(validFile, invalidFile), null);

        assertEquals(1, result.size());
        assertEquals("Good.java", result.get(0).getName());
        verify(codeFileRepository, times(1)).save(any(CodeFile.class));
    }
}
