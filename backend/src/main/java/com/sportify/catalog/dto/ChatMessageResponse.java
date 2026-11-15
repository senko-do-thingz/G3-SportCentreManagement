package com.sportify.catalog.dto;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long id,
        String senderType,
        String content,
        LocalDateTime sentAt
) {}
