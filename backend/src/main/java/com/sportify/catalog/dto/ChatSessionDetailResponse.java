package com.sportify.catalog.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ChatSessionDetailResponse(
        Long id,
        String title,
        boolean isArchived,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ChatMessageResponse> messages
) {}
