package com.sportify.catalog.dto;

import java.time.LocalDateTime;

public record ChatSessionResponse(
        Long id,
        String title,
        boolean isArchived,
        int messageCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
