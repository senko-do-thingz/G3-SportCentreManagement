package com.sportify.catalog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SporiAiServiceTest {

    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private SporiAiService sporiAiService;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        objectMapper = new ObjectMapper();
        sporiAiService = new SporiAiService(
                restTemplate,
                objectMapper,
                "test-api-key",
                "gemini-1.5-flash",
                "https://generativelanguage.googleapis.com/v1beta/models/"
        );
    }

    @Test
    @DisplayName("Should successfully send prompt with hidden role prefix and return reply")
    void shouldSendPromptWithHiddenRoleAndReturnReply() throws Exception {
        String geminiResponseJson = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "You can book courts under the schedule tab."
                          }
                        ],
                        "role": "model"
                      },
                      "finishReason": "STOP"
                    }
                  ]
                }
                """;

        when(restTemplate.postForEntity(
                eq("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>(geminiResponseJson, HttpStatus.OK));

        String reply = sporiAiService.chat("How do I book a court?", "MEMBER");

        assertThat(reply).isEqualTo("You can book courts under the schedule tab.");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(
                eq("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"),
                captor.capture(),
                eq(String.class)
        );

        HttpEntity<Map<String, Object>> captured = captor.getValue();
        HttpHeaders headers = captured.getHeaders();
        assertThat(headers.getFirst("x-goog-api-key")).isEqualTo("test-api-key");
        assertThat(headers.getContentType().toString()).isEqualTo("application/json");

        Map<String, Object> body = captured.getBody();
        assertThat(body).isNotNull();

        // 1. Verify generationConfig has temperature = 0.1
        Map<?, ?> generationConfig = (Map<?, ?>) body.get("generationConfig");
        assertThat(generationConfig).isNotNull();
        assertThat(generationConfig.get("temperature")).isEqualTo(0.1);

        // 2. Verify systemInstruction contains the exact prompt
        Map<?, ?> systemInstruction = (Map<?, ?>) body.get("systemInstruction");
        assertThat(systemInstruction).isNotNull();
        List<?> sysParts = (List<?>) systemInstruction.get("parts");
        Map<?, ?> firstSysPart = (Map<?, ?>) sysParts.get(0);
        assertThat(firstSysPart.get("text")).isEqualTo(SporiAiService.SYSTEM_INSTRUCTION);

        // 3. Verify hidden user role prefix in contents
        List<?> contents = (List<?>) body.get("contents");
        assertThat(contents).hasSize(1);
        Map<?, ?> content = (Map<?, ?>) contents.get(0);
        assertThat(content.get("role")).isEqualTo("user");
        List<?> userParts = (List<?>) content.get("parts");
        Map<?, ?> firstUserPart = (Map<?, ?>) userParts.get(0);
        assertThat(firstUserPart.get("text")).isEqualTo("[User Role: MEMBER] How do I book a court?");

        // Kiem tra system instruction tuan thu quy tac tieng Anh mac dinh, chi huong dan (Guide-Only) va duong dan that cua 4 vai tro
        assertThat(SporiAiService.SYSTEM_INSTRUCTION)
                .contains("ENGLISH-FIRST")
                .contains("READ-ONLY GUIDE")
                .contains("/manager")
                .contains("/coach/profile")
                .contains("/reception/inbox")
                .contains("/member/bookings");
    }

    @Test
    @DisplayName("Should normalize ROLE_ prefix and handle null role gracefully")
    void shouldNormalizeRolePrefix() {
        Map<String, Object> payload1 = sporiAiService.buildGeminiPayload("[User Role: COACH] View student list");
        Map<?, ?> config = (Map<?, ?>) payload1.get("generationConfig");
        assertThat(config.get("temperature")).isEqualTo(0.1);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("""
                        {
                          "candidates": [
                            {
                              "content": {
                                "parts": [{"text": "Your teaching schedule has been updated."}]
                              }
                            }
                          ]
                        }
                        """, HttpStatus.OK));

        String reply = sporiAiService.chat("View schedule", "ROLE_COACH");
        assertThat(reply).isEqualTo("Your teaching schedule has been updated.");
    }

    @Test
    @DisplayName("Should return mandatory refusal when Gemini blocks the prompt")
    void shouldReturnMandatoryRefusalWhenPromptIsBlocked() {
        String blockedResponse = """
                {
                  "promptFeedback": {
                    "blockReason": "SAFETY"
                  }
                }
                """;

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(blockedResponse, HttpStatus.OK));

        String reply = sporiAiService.chat("Guide to attack the system", "MEMBER");
        assertThat(reply).isEqualTo(SporiAiService.MANDATORY_OUT_OF_SCOPE_REPLY);
        assertThat(reply).contains("http://localhost:5173/member/support");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when message is blank")
    void shouldThrowWhenMessageIsBlank() {
        assertThatThrownBy(() -> sporiAiService.chat("   ", "MEMBER"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should throw IllegalStateException when API key is missing")
    void shouldThrowWhenApiKeyIsMissing() {
        SporiAiService noKeyService = new SporiAiService(
                restTemplate,
                objectMapper,
                "",
                "gemini-1.5-flash",
                "https://generativelanguage.googleapis.com/v1beta/models/"
        );

        assertThatThrownBy(() -> noKeyService.chat("Hello", "MEMBER"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GEMINI_API_KEY is not configured");
    }
}
