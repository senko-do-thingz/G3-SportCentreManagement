package com.sportify.catalog.dto;

import com.sportify.catalog.entity.CardStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FrontDeskCardResponse(Long id, String cardCode, Long memberId, String memberCode,
        String memberName, String tierCode, String tierName, CardStatus status,
        LocalDate startDate, LocalDate endDate, BigDecimal amount, LocalDateTime requestedAt) {
}
