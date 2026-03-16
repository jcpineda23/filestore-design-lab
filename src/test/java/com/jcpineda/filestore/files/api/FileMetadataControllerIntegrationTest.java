package com.jcpineda.filestore.files.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcpineda.filestore.auth.api.LoginRequest;
import com.jcpineda.filestore.auth.api.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FileMetadataControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/files"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createAndListFilesForCurrentUser() throws Exception {
        String token = registerAndLogin("owner@example.com", "secret123");

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "hello.txt",
            MediaType.TEXT_PLAIN_VALUE,
            "hello-world".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/files")
                .file(file)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.fileName").value("hello.txt"))
            .andExpect(jsonPath("$.status").value("READY"));

        mockMvc.perform(get("/api/v1/files")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].fileName").value("hello.txt"));
    }

    @Test
    void createIsReplayedForSameIdempotencyKeyAndSamePayload() throws Exception {
        String token = registerAndLogin("idempotent@example.com", "secret123");
        String key = "idem-create-1";

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "same.txt",
            MediaType.TEXT_PLAIN_VALUE,
            "same-content".getBytes()
        );

        MvcResult first = mockMvc.perform(multipart("/api/v1/files")
                .file(file)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key))
            .andExpect(status().isCreated())
            .andReturn();

        MvcResult second = mockMvc.perform(multipart("/api/v1/files")
                .file(file)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key))
            .andExpect(status().isCreated())
            .andReturn();

        String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();
        String secondId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();

        org.assertj.core.api.Assertions.assertThat(firstId).isEqualTo(secondId);

        mockMvc.perform(get("/api/v1/files")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void createReturnsConflictForSameIdempotencyKeyWithDifferentPayload() throws Exception {
        String token = registerAndLogin("idempotent-conflict@example.com", "secret123");
        String key = "idem-create-2";

        MockMultipartFile file1 = new MockMultipartFile(
            "file",
            "first.txt",
            MediaType.TEXT_PLAIN_VALUE,
            "first-content".getBytes()
        );
        MockMultipartFile file2 = new MockMultipartFile(
            "file",
            "second.txt",
            MediaType.TEXT_PLAIN_VALUE,
            "second-content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/files")
                .file(file1)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key))
            .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/v1/files")
                .file(file2)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void ownerCanDeleteOwnFile() throws Exception {
        String token = registerAndLogin("delete-owner@example.com", "secret123");

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "to-delete.txt",
            MediaType.TEXT_PLAIN_VALUE,
            "temporary".getBytes()
        );

        MvcResult createResult = mockMvc.perform(multipart("/api/v1/files")
                .file(file)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated())
            .andReturn();

        String fileId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/v1/files/{fileId}", fileId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/files")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void ownerCanDownloadStoredFileBytes() throws Exception {
        String token = registerAndLogin("download-owner@example.com", "secret123");

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "download.txt",
            MediaType.TEXT_PLAIN_VALUE,
            "download-body".getBytes()
        );

        MvcResult createResult = mockMvc.perform(multipart("/api/v1/files")
                .file(file)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated())
            .andReturn();

        String fileId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/files/{fileId}/download", fileId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.TEXT_PLAIN))
            .andExpect(content().bytes("download-body".getBytes()));
    }

    @Test
    void userCannotDeleteAnotherUsersFile() throws Exception {
        String ownerToken = registerAndLogin("owner2@example.com", "secret123");
        String attackerToken = registerAndLogin("attacker@example.com", "secret123");

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "private.txt",
            MediaType.TEXT_PLAIN_VALUE,
            "private".getBytes()
        );

        MvcResult createResult = mockMvc.perform(multipart("/api/v1/files")
                .file(file)
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isCreated())
            .andReturn();

        String fileId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/v1/files/{fileId}", fileId)
                .header("Authorization", "Bearer " + attackerToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    private String registerAndLogin(String email, String password) throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(email, password);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest(email, password);
        MvcResult loginResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode node = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return node.get("token").asText();
    }
}
