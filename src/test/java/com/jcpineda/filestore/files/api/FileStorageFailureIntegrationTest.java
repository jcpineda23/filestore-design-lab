package com.jcpineda.filestore.files.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcpineda.filestore.auth.api.LoginRequest;
import com.jcpineda.filestore.auth.api.RegisterRequest;
import com.jcpineda.filestore.files.domain.FileEntity;
import com.jcpineda.filestore.files.domain.FileStatus;
import com.jcpineda.filestore.files.persistence.FileRepository;
import com.jcpineda.filestore.storage.InMemoryObjectStorageService;
import com.jcpineda.filestore.storage.ObjectStorageService;
import com.jcpineda.filestore.storage.StoredObject;
import com.jcpineda.filestore.storage.StorageObjectMetadata;
import com.jcpineda.filestore.storage.StorageOperationException;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(FileStorageFailureIntegrationTest.StorageFailureTestConfig.class)
class FileStorageFailureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private ControllableObjectStorageService objectStorageService;

    @AfterEach
    void resetStorageBehavior() {
        objectStorageService.setAvailable();
    }

    @Test
    void createReturnsServiceUnavailableAndPersistsFailedMetadataWhenStorageIsUnavailable() throws Exception {
        AuthSession session = registerAndLogin("upload-failure@example.com", "secret123");
        objectStorageService.failAllOperations();

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "failed-upload.txt",
            MediaType.TEXT_PLAIN_VALUE,
            "cannot-store".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/files")
                .file(file)
                .header("Authorization", "Bearer " + session.token()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error.code").value("STORAGE_UNAVAILABLE"));

        FileEntity persisted = fileRepository.findAllByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(session.userId())
            .getFirst();

        assertThat(persisted.getStatus()).isEqualTo(FileStatus.FAILED);
        assertThat(persisted.getFailureReason()).isEqualTo("STORAGE_UNAVAILABLE");
        assertThat(persisted.getDeletedAt()).isNull();
    }

    @Test
    void downloadReturnsServiceUnavailableWhenStorageIsUnavailable() throws Exception {
        AuthSession session = registerAndLogin("download-failure@example.com", "secret123");
        String fileId = createFile(session.token(), "download.txt", "download-body");

        objectStorageService.failReads();

        mockMvc.perform(get("/api/v1/files/{fileId}/download", fileId)
                .header("Authorization", "Bearer " + session.token()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error.code").value("STORAGE_UNAVAILABLE"));
    }

    @Test
    void deleteReturnsServiceUnavailableAndAllowsRetryAfterStorageRecovers() throws Exception {
        AuthSession session = registerAndLogin("delete-failure@example.com", "secret123");
        String fileId = createFile(session.token(), "delete.txt", "delete-me");

        objectStorageService.failDeletes();

        mockMvc.perform(delete("/api/v1/files/{fileId}", fileId)
                .header("Authorization", "Bearer " + session.token()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error.code").value("STORAGE_UNAVAILABLE"));

        FileEntity failedDelete = fileRepository.findById(UUID.fromString(fileId)).orElseThrow();
        assertThat(failedDelete.getStatus()).isEqualTo(FileStatus.DELETE_FAILED);
        assertThat(failedDelete.getDeletedAt()).isNull();
        assertThat(failedDelete.getFailureReason()).isEqualTo("STORAGE_UNAVAILABLE");

        objectStorageService.setAvailable();

        mockMvc.perform(delete("/api/v1/files/{fileId}", fileId)
                .header("Authorization", "Bearer " + session.token()))
            .andExpect(status().isNoContent());

        FileEntity deleted = fileRepository.findById(UUID.fromString(fileId)).orElseThrow();
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(deleted.getFailureReason()).isNull();
    }

    @Test
    void uploadSucceedsAgainAfterStorageRecoversWithoutRestartingTheApp() throws Exception {
        AuthSession session = registerAndLogin("recovery@example.com", "secret123");
        objectStorageService.failWrites();

        MockMultipartFile failedFile = new MockMultipartFile(
            "file",
            "before-recovery.txt",
            MediaType.TEXT_PLAIN_VALUE,
            "down".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/files")
                .file(failedFile)
                .header("Authorization", "Bearer " + session.token()))
            .andExpect(status().isServiceUnavailable());

        objectStorageService.setAvailable();

        MockMultipartFile recoveredFile = new MockMultipartFile(
            "file",
            "after-recovery.txt",
            MediaType.TEXT_PLAIN_VALUE,
            "up".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/files")
                .file(recoveredFile)
                .header("Authorization", "Bearer " + session.token()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("READY"));
    }

    private String createFile(String token, String fileName, String body) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            fileName,
            MediaType.TEXT_PLAIN_VALUE,
            body.getBytes()
        );

        MvcResult createResult = mockMvc.perform(multipart("/api/v1/files")
                .file(file)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated())
            .andReturn();

        return objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();
    }

    private AuthSession registerAndLogin(String email, String password) throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(email, password);
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest(email, password);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode node = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return new AuthSession(UUID.fromString(node.get("userId").asText()), node.get("token").asText());
    }

    private record AuthSession(UUID userId, String token) {
    }

    @TestConfiguration
    static class StorageFailureTestConfig {

        @Bean
        @Primary
        ControllableObjectStorageService objectStorageService() {
            return new ControllableObjectStorageService();
        }
    }

    static class ControllableObjectStorageService implements ObjectStorageService {

        private final InMemoryObjectStorageService delegate = new InMemoryObjectStorageService();
        private volatile boolean failWrites;
        private volatile boolean failReads;
        private volatile boolean failDeletes;

        void failAllOperations() {
            this.failWrites = true;
            this.failReads = true;
            this.failDeletes = true;
        }

        void failWrites() {
            this.failWrites = true;
        }

        void failReads() {
            this.failReads = true;
        }

        void failDeletes() {
            this.failDeletes = true;
        }

        void setAvailable() {
            this.failWrites = false;
            this.failReads = false;
            this.failDeletes = false;
        }

        @Override
        public StorageObjectMetadata putObject(String objectKey, MultipartFile file) throws IOException {
            if (failWrites) {
                throw new StorageOperationException("Simulated storage outage during put", null);
            }
            return delegate.putObject(objectKey, file);
        }

        @Override
        public StoredObject getObject(String objectKey) throws IOException {
            if (failReads) {
                throw new StorageOperationException("Simulated storage outage during get", null);
            }
            return delegate.getObject(objectKey);
        }

        @Override
        public void deleteObject(String objectKey) throws IOException {
            if (failDeletes) {
                throw new StorageOperationException("Simulated storage outage during delete", null);
            }
            delegate.deleteObject(objectKey);
        }
    }
}
