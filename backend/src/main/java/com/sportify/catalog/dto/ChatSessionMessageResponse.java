package com.sportify.catalog.dto;

public record ChatSessionMessageResponse(
        Long sessionId,
        ChatMessageResponse userMessage,
        ChatMessageResponse assistantMessage
) {}
