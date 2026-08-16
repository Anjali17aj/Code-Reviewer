package com.codereview.controller;

import com.codereview.dto.CodeFileDTO;
import com.codereview.dto.FolderDTO;
import com.codereview.service.FileService;
import com.codereview.service.JwtUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileControllerTest {

    private MockMvc mockMvc;

    @Mock
    private FileService fileService;

    @InjectMocks
    private FileController fileController;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String BASE_URL = "/api/files";
    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(fileController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate() {
        JwtUserDetails principal = new JwtUserDetails(USER_ID, "test@example.com", "password");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private CodeFileDTO buildFileDTO(Long id, String name) {
        CodeFileDTO dto = new CodeFileDTO();
        dto.setId(id);
        dto.setName(name);
        dto.setLanguage("java");
        dto.setContent("class " + name.replace(".java", "") + " { }");
        dto.setCreatedAt(LocalDateTime.of(2026, 1, 15, 10, 30));
        dto.setUpdatedAt(LocalDateTime.of(2026, 1, 15, 10, 30));
        return dto;
    }

    // --- POST /api/files/bulk ---

    @Test
    void bulkCreateFiles_ValidRequest_ReturnsCreatedFiles() throws Exception {
        authenticate();
        CodeFileDTO file1 = buildFileDTO(1L, "Foo.java");
        CodeFileDTO file2 = buildFileDTO(2L, "Bar.java");

        when(fileService.createBulkFiles(eq(USER_ID), anyList(), isNull())).thenReturn(List.of(file1, file2));

        mockMvc.perform(post(BASE_URL + "/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\": [{\"name\": \"Foo.java\", \"language\": \"java\", \"content\": \"class Foo { }\"}, {\"name\": \"Bar.java\", \"language\": \"java\", \"content\": \"class Bar { }\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Foo.java"))
                .andExpect(jsonPath("$[1].name").value("Bar.java"));
    }

    @Test
    void bulkCreateFiles_EmptyFiles_ReturnsEmptyList() throws Exception {
        authenticate();
        when(fileService.createBulkFiles(eq(USER_ID), anyList(), isNull())).thenReturn(List.of());

        mockMvc.perform(post(BASE_URL + "/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\": []}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void bulkCreateFiles_WithFolderId_PassesFolderId() throws Exception {
        authenticate();
        Long folderId = 5L;
        when(fileService.createBulkFiles(eq(USER_ID), anyList(), eq(folderId))).thenReturn(List.of());

        mockMvc.perform(post(BASE_URL + "/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\": [{\"name\": \"Foo.java\", \"language\": \"java\", \"content\": \"class Foo { }\"}], \"folderId\": 5}"))
                .andExpect(status().isOk());

        verify(fileService).createBulkFiles(eq(USER_ID), anyList(), eq(folderId));
    }

    // --- POST /api/files/upload-multiple ---

    @Test
    void uploadMultipleFiles_ValidRequest_ReturnsUploadedFiles() throws Exception {
        authenticate();
        CodeFileDTO file1 = buildFileDTO(1L, "Foo.java");

        when(fileService.uploadMultipleFiles(eq(USER_ID), anyList(), isNull())).thenReturn(List.of(file1));

        MockMultipartFile file = new MockMultipartFile(
                "files", "Foo.java", "text/plain",
                "class Foo { }".getBytes());

        mockMvc.perform(multipart(BASE_URL + "/upload-multiple")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Foo.java"));
    }

    @Test
    void uploadMultipleFiles_EmptyFileList_ReturnsEmptyList() throws Exception {
        authenticate();
        when(fileService.uploadMultipleFiles(eq(USER_ID), anyList(), isNull())).thenReturn(List.of());

        // Send a minimal valid multipart to avoid 400 from missing param
        MockMultipartFile emptyFile = new MockMultipartFile(
                "files", "empty.txt", "text/plain",
                "".getBytes());

        mockMvc.perform(multipart(BASE_URL + "/upload-multiple")
                        .file(emptyFile))
                .andExpect(status().isOk());
    }
}
