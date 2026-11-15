package com.sportify.catalog.controller;

import com.sportify.catalog.dto.*;
import com.sportify.catalog.service.SporiAiService;
import com.sportify.catalog.service.SporiAiSessionService;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SporiAiControllerTest {

    private SporiAiService sporiAiService;
    private SporiAiSessionService sessionService;
    private SporiAiController controller;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        sporiAiService = mock(SporiAiService.class);
        sessionService = mock(SporiAiSessionService.class);
        controller = new SporiAiController(sporiAiService, sessionService);

        Role memberRole = Role.builder().code("MEMBER").name("Member").build();
        UserAccount account = UserAccount.builder().id(101L).email("member@example.com").role(memberRole).status("ACTIVE").build();
        userDetails = new CustomUserDetails(account);
    }

    @Test
    @DisplayName("Should create session successfully")
    void shouldCreateSession() {
        ChatSessionResponse mockResponse = new ChatSessionResponse(1L, "New Chat", false, 0, LocalDateTime.now(), LocalDateTime.now());
        when(sessionService.createSession(101L, "New Chat")).thenReturn(mockResponse);

        ResponseEntity<ChatSessionResponse> response = controller.createSession(userDetails, new ChatSessionCreateRequest("New Chat"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().title()).isEqualTo("New Chat");
    }

    @Test
    @DisplayName("Should list sessions successfully")
    void shouldListSessions() {
        ChatSessionResponse s1 = new ChatSessionResponse(1L, "Chat 1", false, 2, LocalDateTime.now(), LocalDateTime.now());
        when(sessionService.listSessions(101L, false)).thenReturn(List.of(s1));

        ResponseEntity<List<ChatSessionResponse>> response = controller.listSessions(userDetails, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("Should rename session successfully")
    void shouldRenameSession() {
        ChatSessionResponse s1 = new ChatSessionResponse(1L, "Renamed Chat", false, 2, LocalDateTime.now(), LocalDateTime.now());
        when(sessionService.renameSession(101L, 1L, "Renamed Chat")).thenReturn(s1);

        ResponseEntity<ChatSessionResponse> response = controller.renameSession(userDetails, 1L, new ChatSessionRenameRequest("Renamed Chat"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().title()).isEqualTo("Renamed Chat");
    }

    @Test
    @DisplayName("Should archive session successfully")
    void shouldArchiveSession() {
        ChatSessionResponse s1 = new ChatSessionResponse(1L, "Chat 1", true, 2, LocalDateTime.now(), LocalDateTime.now());
        when(sessionService.archiveSession(101L, 1L, true)).thenReturn(s1);

        ResponseEntity<ChatSessionResponse> response = controller.archiveSession(userDetails, 1L, new ChatSessionArchiveRequest(true));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isArchived()).isTrue();
    }

    @Test
    @DisplayName("Should delete session successfully")
    void shouldDeleteSession() {
        ResponseEntity<Void> response = controller.deleteSession(userDetails, 1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(sessionService).deleteSession(101L, 1L);
    }

    @Test
    @DisplayName("Should send message in session and return turns")
    void shouldSendMessageInSession() {
        ChatMessageResponse userMsg = new ChatMessageResponse(1L, "USER", "Hello Spori", LocalDateTime.now());
        ChatMessageResponse assistantMsg = new ChatMessageResponse(2L, "ASSISTANT", "Hello Member", LocalDateTime.now());
        ChatSessionMessageResponse mockRes = new ChatSessionMessageResponse(10L, userMsg, assistantMsg);

        when(sessionService.sendMessage(101L, "MEMBER", 10L, "Hello Spori")).thenReturn(mockRes);

        ResponseEntity<ChatSessionMessageResponse> response = controller.sendMessageInSession(
                userDetails, 10L, new ChatSessionMessageRequest("Hello Spori")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().assistantMessage().content()).isEqualTo("Hello Member");
    }

    @Test
    @DisplayName("Should extract role from UserDetails and return Spori reply")
    void shouldExtractRoleAndReturnReply() {
        SporiAiChatRequest request = new SporiAiChatRequest("Which package is suitable for me?");
        when(sporiAiService.chat("Which package is suitable for me?", "MEMBER"))
                .thenReturn("We have a diverse range of packages...");

        ResponseEntity<SporiAiChatResponse> response = controller.chat(userDetails, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().reply()).isEqualTo("We have a diverse range of packages...");
        assertThat(response.getBody().role()).isEqualTo("MEMBER");

        verify(sporiAiService).chat("Which package is suitable for me?", "MEMBER");
    }

    @Test
    @DisplayName("Should handle COACH role accurately")
    void shouldHandleCoachRole() {
        Role coachRole = Role.builder().code("COACH").name("Coach").build();
        UserAccount account = UserAccount.builder().id(202L).email("coach@example.com").role(coachRole).status("ACTIVE").build();
        CustomUserDetails coachUserDetails = new CustomUserDetails(account);

        SporiAiChatRequest request = new SporiAiChatRequest("View tomorrow's teaching schedule");
        when(sporiAiService.chat("View tomorrow's teaching schedule", "COACH"))
                .thenReturn("Tomorrow's schedule has 2 slots.");

        ResponseEntity<SporiAiChatResponse> response = controller.chat(coachUserDetails, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().role()).isEqualTo("COACH");
        assertThat(response.getBody().reply()).isEqualTo("Tomorrow's schedule has 2 slots.");
    }

    @Test
    @DisplayName("Should return status response when question is /status/")
    void shouldReturnStatusResponseWhenQuestionIsStatus() {
        SporiAiChatRequest request = new SporiAiChatRequest("/status/");
        when(sporiAiService.getStatus()).thenReturn("Gemini API: CONNECTED. Spori AI is active using model: gemini-flash-lite-latest");

        ResponseEntity<SporiAiChatResponse> response = controller.chat(userDetails, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().reply()).contains("CONNECTED");
    }
}
