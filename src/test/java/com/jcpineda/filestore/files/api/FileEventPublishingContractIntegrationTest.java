package com.jcpineda.filestore.files.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcpineda.filestore.auth.api.LoginRequest;
import com.jcpineda.filestore.auth.api.RegisterRequest;
import com.jcpineda.filestore.events.DomainEvent;
import com.jcpineda.filestore.events.UserEventPublisher;
import com.jcpineda.filestore.storage.InMemoryObjectStorageService;
import com.jcpineda.filestore.storage.ObjectStorageService;
import com.jcpineda.filestore.storage.StoredObject;
import com.jcpineda.filestore.storage.StorageObjectMetadata;
import com.jcpineda.filestore.storage.StorageOperationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.multipart.MultipartFile;
import org.mockito.ArgumentCaptor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FileEventPublishingContractIntegrationTest.ContractTestConfig.class)
class FileEventPublishingContractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ControllableObjectStorageService objectStorageService;

    @SpyBean
    private UserEventPublisher userEventPublisher;

    @AfterEach
    void resetTestDoubles() {
        objectStorageService.setAvailable();
        clearInvocations(userEventPublisher);
    }

    @Test
    void uploadSuccessPublishesStartedProgressCompletedInOrder() throws Exception {
        AuthSession session = registerAndLogin("publish-success-" + UUID.randomUUID() + "@example.com", "secret123");
        String correlationId = "corr-upload-success";
        String fileId = uploadFile(session.token(), "ordered-upload.txt", "hello-world", correlationId, status().isCreated());

        List<DomainEvent> events = capturedEvents(3);

        assertThat(events).extracting(DomainEvent::eventType)
            .containsExactly("file.upload.started", "file.upload.progress", "file.upload.completed");

        assertCommonFileEvent(events.get(0), session.userId(), fileId, correlationId);
        assertCommonFileEvent(events.get(1), session.userId(), fileId, correlationId);
        assertCommonFileEvent(events.get(2), session.userId(), fileId, correlationId);

        assertThat(data(events.get(0)).path("fileName").asText()).isEqualTo("ordered-upload.txt");
        assertThat(data(events.get(1)).path("percent").asInt()).isEqualTo(100);
        assertThat(data(events.get(1)).path("bytesReceived").asLong()).isEqualTo(11L);
        assertThat(data(events.get(2)).path("checksum").asText()).isNotBlank();
    }

    @Test
    void uploadFailurePublishesStartedThenFailed() throws Exception {
        AuthSession session = registerAndLogin("publish-failure-" + UUID.randomUUID() + "@example.com", "secret123");
        String correlationId = "corr-upload-failure";

        objectStorageService.failWrites();

        uploadFile(session.token(), "failed-upload.txt", "boom", correlationId, status().isServiceUnavailable());

        List<DomainEvent> events = capturedEvents(2);

        assertThat(events).extracting(DomainEvent::eventType)
            .containsExactly("file.upload.started", "file.upload.failed");

        String fileId = events.getFirst().resourceId();
        assertCommonFileEvent(events.get(0), session.userId(), fileId, correlationId);
        assertCommonFileEvent(events.get(1), session.userId(), fileId, correlationId);
        assertThat(data(events.get(1)).path("reason").asText()).isEqualTo("STORAGE_UNAVAILABLE");
    }

    @Test
    void deleteSuccessPublishesDeletedWithDeleteCorrelationId() throws Exception {
        AuthSession session = registerAndLogin("publish-delete-" + UUID.randomUUID() + "@example.com", "secret123");
        String fileId = uploadFile(session.token(), "delete-me.txt", "to-delete", "corr-delete-upload", status().isCreated());

        clearInvocations(userEventPublisher);

        String deleteCorrelationId = "corr-delete-success";
        mockMvc.perform(delete("/api/v1/files/{fileId}", fileId)
                .header("Authorization", "Bearer " + session.token())
                .header("X-Correlation-Id", deleteCorrelationId))
            .andExpect(status().isNoContent());

        List<DomainEvent> events = capturedEvents(1);

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().eventType()).isEqualTo("file.deleted");
        assertCommonFileEvent(events.getFirst(), session.userId(), fileId, deleteCorrelationId);
        assertThat(data(events.getFirst()).path("fileName").asText()).isEqualTo("delete-me.txt");
    }

    private void assertCommonFileEvent(DomainEvent event,
                                       UUID userId,
                                       String fileId,
                                       String correlationId) {
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.resourceType()).isEqualTo("file");
        assertThat(event.resourceId()).isEqualTo(fileId);
        assertThat(event.correlationId()).isEqualTo(correlationId);
    }

    private JsonNode data(DomainEvent event) {
        return objectMapper.valueToTree(event.data());
    }

    private List<DomainEvent> capturedEvents(int expectedCount) {
        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(userEventPublisher, times(expectedCount)).publish(captor.capture());
        return captor.getAllValues();
    }

    private String uploadFile(String token,
                              String fileName,
                              String body,
                              String correlationId,
                              org.springframework.test.web.servlet.ResultMatcher expectedStatus) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            fileName,
            MediaType.TEXT_PLAIN_VALUE,
            body.getBytes(StandardCharsets.UTF_8)
        );

        MvcResult createResult = mockMvc.perform(multipart("/api/v1/files")
                .file(file)
                .header("Authorization", "Bearer " + token)
                .header("X-Correlation-Id", correlationId))
            .andExpect(expectedStatus)
            .andReturn();

        if (createResult.getResponse().getStatus() != 201) {
            return null;
        }

        return objectMapper.readTree(createResult.getResponse().getContentAsString()).path("id").asText();
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
    static class ContractTestConfig {

        @Bean
        @Primary
        ControllableObjectStorageService objectStorageService() {
            return new ControllableObjectStorageService();
        }
    }

    static class ControllableObjectStorageService implements ObjectStorageService {

        private final InMemoryObjectStorageService delegate = new InMemoryObjectStorageService();
        private volatile boolean failWrites;

        void failWrites() {
            this.failWrites = true;
        }

        void setAvailable() {
            this.failWrites = false;
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
            return delegate.getObject(objectKey);
        }

        @Override
        public void deleteObject(String objectKey) throws IOException {
            delegate.deleteObject(objectKey);
        }
    }
}
