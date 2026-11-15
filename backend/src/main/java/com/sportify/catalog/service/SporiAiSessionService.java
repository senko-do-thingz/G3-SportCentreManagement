package com.sportify.catalog.service;

import com.sportify.catalog.dto.*;
import com.sportify.catalog.entity.AiConversation;
import com.sportify.catalog.entity.AiMessage;
import com.sportify.catalog.repository.AiConversationRepository;
import com.sportify.catalog.repository.AiMessageRepository;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service managing AI chat sessions (create, rename, archive, delete, history).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SporiAiSessionService {

    private static final String DEFAULT_SESSION_TITLE = "New conversation";
    private static final String LEGACY_SESSION_TITLE = "Cuộc trò chuyện mới";

    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SporiAiService sporiAiService;

    @Transactional
    public ChatSessionResponse createSession(Long userId, String initialTitle) {
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        String title = (initialTitle != null && !initialTitle.isBlank())
                ? initialTitle.trim()
                : DEFAULT_SESSION_TITLE;

        LocalDateTime now = LocalDateTime.now();
        AiConversation conversation = AiConversation.builder()
                .user(user)
                .title(title)
                .isArchived(false)
                .isDeleted(false)
                .createdAt(now)
                .updatedAt(now)
                .build();

        AiConversation saved = conversationRepository.save(conversation);
        log.info("Created new AI chat session #{} for user #{}", saved.getId(), userId);

        return toSessionResponse(saved, 0);
    }

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> listSessions(Long userId, Boolean archived) {
        List<AiConversation> list;
        if (archived != null) {
            list = conversationRepository.findByUserIdAndArchived(userId, archived);
        } else {
            list = conversationRepository.findAllActiveByUserId(userId);
        }

        return list.stream()
                .map(conv -> toSessionResponse(conv, conv.getMessages() != null ? conv.getMessages().size() : 0))
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatSessionDetailResponse getSessionDetail(Long userId, Long sessionId) {
        AiConversation session = findUserSession(userId, sessionId);
        List<AiMessage> messages = messageRepository.findByConversationIdOrderBySentAtAsc(sessionId);

        List<ChatMessageResponse> messageResponses = messages.stream()
                .map(this::toMessageResponse)
                .toList();

        return new ChatSessionDetailResponse(
                session.getId(),
                session.getTitle(),
                session.getIsArchived(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                messageResponses
        );
    }

    @Transactional
    public ChatSessionResponse renameSession(Long userId, Long sessionId, String newTitle) {
        AiConversation session = findUserSession(userId, sessionId);

        String trimmed = newTitle.trim();
        session.setTitle(trimmed);
        session.setUpdatedAt(LocalDateTime.now());

        AiConversation saved = conversationRepository.save(session);
        log.info("Renamed AI chat session #{} to: {}", sessionId, trimmed);

        return toSessionResponse(saved, session.getMessages() != null ? session.getMessages().size() : 0);
    }

    @Transactional
    public ChatSessionResponse archiveSession(Long userId, Long sessionId, boolean archive) {
        AiConversation session = findUserSession(userId, sessionId);

        session.setIsArchived(archive);
        session.setUpdatedAt(LocalDateTime.now());

        AiConversation saved = conversationRepository.save(session);
        log.info("Updated archive status for AI chat session #{} to: {}", sessionId, archive);

        return toSessionResponse(saved, session.getMessages() != null ? session.getMessages().size() : 0);
    }

    @Transactional
    public void deleteSession(Long userId, Long sessionId) {
        AiConversation session = findUserSession(userId, sessionId);

        session.setIsDeleted(true);
        session.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(session);

        log.info("Soft deleted AI chat session #{} for user #{}", sessionId, userId);
    }

    @Transactional
    public ChatSessionMessageResponse sendMessage(Long userId, String role, Long sessionId, String userMessage) {
        AiConversation session = findUserSession(userId, sessionId);

        // Load prior messages for conversational memory context
        List<AiMessage> history = messageRepository.findByConversationIdOrderBySentAtAsc(sessionId);

        LocalDateTime now = LocalDateTime.now();
        AiMessage userMsg = AiMessage.builder()
                .conversation(session)
                .senderType("USER")
                .content(userMessage.trim())
                .sentAt(now)
                .build();
        AiMessage savedUserMsg = messageRepository.save(userMsg);

        // Call Gemini API with historical context
        String assistantReply = sporiAiService.chatWithHistory(userMessage, role, history);

        AiMessage assistantMsg = AiMessage.builder()
                .conversation(session)
                .senderType("ASSISTANT")
                .content(assistantReply)
                .sentAt(LocalDateTime.now())
                .build();
        AiMessage savedAssistantMsg = messageRepository.save(assistantMsg);

        // Automatically update title if session currently has the default placeholder title
        if ((session.getTitle() == null
                || session.getTitle().equalsIgnoreCase(DEFAULT_SESSION_TITLE)
                || session.getTitle().equalsIgnoreCase(LEGACY_SESSION_TITLE))
                && history.isEmpty()) {
            String autoTitle = userMessage.trim();
            if (autoTitle.length() > 40) {
                autoTitle = autoTitle.substring(0, 40) + "...";
            }
            session.setTitle(autoTitle);
        }

        session.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(session);

        return new ChatSessionMessageResponse(
                sessionId,
                toMessageResponse(savedUserMsg),
                toMessageResponse(savedAssistantMsg)
        );
    }

    private AiConversation findUserSession(Long userId, Long sessionId) {
        return conversationRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("AI chat session not found with ID: " + sessionId));
    }

    private ChatSessionResponse toSessionResponse(AiConversation conv, int messageCount) {
        return new ChatSessionResponse(
                conv.getId(),
                conv.getTitle(),
                Boolean.TRUE.equals(conv.getIsArchived()),
                messageCount,
                conv.getCreatedAt(),
                conv.getUpdatedAt()
        );
    }

    private ChatMessageResponse toMessageResponse(AiMessage msg) {
        return new ChatMessageResponse(
                msg.getId(),
                msg.getSenderType(),
                msg.getContent(),
                msg.getSentAt()
        );
    }
}
