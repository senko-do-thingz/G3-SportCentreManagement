package com.sportify.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.AbstractIntegrationTest;
import com.sportify.identity.dto.AuthRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class UserControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldReturnUserProfile_whenValidTokenProvided() throws Exception {
        // 1. Login as manager
        AuthRequest loginReq = new AuthRequest("manager@sportify.com", "password");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String responseStr = loginResult.getResponse().getContentAsString();
        Map<String, String> tokens = objectMapper.readValue(responseStr, Map.class);
        String accessToken = tokens.get("accessToken");

        // 2. Access /users/me
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MANAGER"))
                .andExpect(jsonPath("$.email").value("manager@sportify.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void shouldReturn404_whenAccessingNonExistentEndpoint() throws Exception {
        // 1. Login as manager
        AuthRequest loginReq = new AuthRequest("manager@sportify.com", "password");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String responseStr = loginResult.getResponse().getContentAsString();
        Map<String, String> tokens = objectMapper.readValue(responseStr, Map.class);
        String accessToken = tokens.get("accessToken");

        // 2. Access /anything
        mockMvc.perform(get("/api/v1/anything")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }
}
