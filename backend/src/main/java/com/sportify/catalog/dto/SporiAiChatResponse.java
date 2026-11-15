package com.sportify.catalog.dto;

public record SporiAiChatResponse(
        String reply,
        String role
) {
    public SporiAiChatResponse(String reply) {
        this(reply, null);
    }
}
