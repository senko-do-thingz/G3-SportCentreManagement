package com.sportify.catalog.service;

import com.sportify.catalog.dto.ChatSessionDetailResponse;
import com.sportify.catalog.dto.ChatSessionMessageResponse;
import com.sportify.catalog.dto.ChatSessionResponse;
import com.sportify.catalog.entity.AiConversation;
import com.sportify.catalog.entity.AiMessage;
import com.sportify.catalog.repository.AiConversationRepository;
import com.sportify.catalog.repository.AiMessageRepository;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SporiAiSessionServiceTest {

    private AiConversationRepository conversationRepository;
    private AiMessageRepository messageRepository;
    private UserRepository userRepository;
    private SporiAiService sporiAiService;
    private SporiAiSessionService sessionService;

    private UserAccount mockUser;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(AiConversationRepository.class);
        messageRepository = mock(AiMessageRepository.class);
        userRepository = mock(UserRepository.class);
        sporiAiService = mock(SporiAiService.class);

        sessionService = new SporiAiSessionService(
                conversationRepository,
                messageRepository,
                userRepository,
                sporiAiService
        );

        mockUser = UserAccount.builder()
                .id(1L)
                .email("member@example.com")
                .role(Role.builder().code("MEMBER").build())
                .build();
    }

    @Test
    @DisplayName("Should create a new chat session with default title when title is empty")
    void shouldCreateSessionWithDefaultTitle() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(conversationRepository.save(any(AiConversation.class))).thenAnswer(invocation -> {
            AiConversation conv = invocation.getArgument(0);
            conv.setId(10L);
            return conv;
        });

        ChatSessionResponse response = sessionService.createSession(1L, null);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.title()).isEqualTo("New conversation");
        assertThat(response.isArchived()).isFalse();
        assertThat(response.messageCount()).isEqualTo(0);

        verify(conversationRepository).save(argThat(c ->
                c.getUser().equals(mockUser) &&
                !c.getIsArchived() &&
                !c.getIsDeleted()
        ));
    }

    @Test
    @DisplayName("Should list active and archived chat sessions")
    void shouldListSessions() {
        AiConversation c1 = AiConversation.builder()
                .id(1L)
                .user(mockUser)
                .title("Session 1")
                .isArchived(false)
                .isDeleted(false)
                .messages(new ArrayList<>())
                .build();

        AiConversation c2 = AiConversation.builder()
                .id(2L)
                .user(mockUser)
                .title("Session 2 (Archived)")
                .isArchived(true)
                .isDeleted(false)
                .messages(new ArrayList<>())
                .build();

        when(conversationRepository.findAllActiveByUserId(1L)).thenReturn(List.of(c1, c2));
        when(conversationRepository.findByUserIdAndArchived(1L, true)).thenReturn(List.of(c2));

        List<ChatSessionResponse> all = sessionService.listSessions(1L, null);
        assertThat(all).hasSize(2);

        List<ChatSessionResponse> archived = sessionService.listSessions(1L, true);
        assertThat(archived).hasSize(1);
        assertThat(archived.get(0).title()).isEqualTo("Session 2 (Archived)");
    }

    @Test
    @DisplayName("Should rename session successfully")
    void shouldRenameSession() {
        AiConversation session = AiConversation.builder()
                .id(10L)
                .user(mockUser)
                .title("Old Title")
                .isArchived(false)
                .isDeleted(false)
                .build();

        when(conversationRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(session));
        when(conversationRepository.save(any(AiConversation.class))).thenAnswer(i -> i.getArgument(0));

        ChatSessionResponse response = sessionService.renameSession(1L, 10L, "New Title");

        assertThat(response.title()).isEqualTo("New Title");
        verify(conversationRepository).save(argThat(c -> "New Title".equals(c.getTitle())));
    }

    @Test
    @DisplayName("Should archive and unarchive session")
    void shouldArchiveSession() {
        AiConversation session = AiConversation.builder()
                .id(10L)
                .user(mockUser)
                .title("Session 10")
                .isArchived(false)
                .isDeleted(false)
                .build();

        when(conversationRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(session));
        when(conversationRepository.save(any(AiConversation.class))).thenAnswer(i -> i.getArgument(0));

        ChatSessionResponse archived = sessionService.archiveSession(1L, 10L, true);
        assertThat(archived.isArchived()).isTrue();

        ChatSessionResponse unarchived = sessionService.archiveSession(1L, 10L, false);
        assertThat(unarchived.isArchived()).isFalse();
    }

    @Test
    @DisplayName("Should soft delete session")
    void shouldDeleteSession() {
        AiConversation session = AiConversation.builder()
                .id(10L)
                .user(mockUser)
                .title("Session 10")
                .isArchived(false)
                .isDeleted(false)
                .build();

        when(conversationRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(session));

        sessionService.deleteSession(1L, 10L);

        verify(conversationRepository).save(argThat(AiConversation::getIsDeleted));
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when session does not exist")
    void shouldThrowWhenSessionNotFound() {
        when(conversationRepository.findByIdAndUserId(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.getSessionDetail(1L, 999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("Should send message in session, save turns and auto-title initial conversation")
    void shouldSendMessageAndAutoTitle() {
        AiConversation session = AiConversation.builder()
                .id(10L)
                .user(mockUser)
                .title("New conversation")
                .isArchived(false)
                .isDeleted(false)
                .build();

        when(conversationRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(session));
        when(messageRepository.findByConversationIdOrderBySentAtAsc(10L)).thenReturn(List.of());

        when(messageRepository.save(any(AiMessage.class))).thenAnswer(i -> {
            AiMessage msg = i.getArgument(0);
            msg.setId(100L);
            return msg;
        });

        when(sporiAiService.chatWithHistory(eq("How do I book a badminton court?"), eq("MEMBER"), any()))
                .thenReturn("Please visit the court booking page to select a slot.");

        ChatSessionMessageResponse response = sessionService.sendMessage(
                1L, "MEMBER", 10L, "How do I book a badminton court?"
        );

        assertThat(response.sessionId()).isEqualTo(10L);
        assertThat(response.userMessage().content()).isEqualTo("How do I book a badminton court?");
        assertThat(response.assistantMessage().content()).isEqualTo("Please visit the court booking page to select a slot.");

        // Verify auto-title
        assertThat(session.getTitle()).isEqualTo("How do I book a badminton court?");
        verify(conversationRepository).save(session);
    }
}
