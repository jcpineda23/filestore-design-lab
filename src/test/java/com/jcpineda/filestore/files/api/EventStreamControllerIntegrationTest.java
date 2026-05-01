package com.jcpineda.filestore.files.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EventStreamControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void eventStreamRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/events/stream"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanOpenEventStream() throws Exception {
        String token = registerAndLogin("sse@example.com", "secret123");

        MvcResult result = mockMvc.perform(get("/api/v1/events/stream")
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(request().asyncStarted())
            .andReturn();

        result.getRequest().getAsyncContext().complete();
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
