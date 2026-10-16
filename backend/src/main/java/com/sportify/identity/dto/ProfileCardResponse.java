package com.sportify.identity.dto;

import java.time.LocalDate;

public record ProfileCardResponse(Long id, String tierCode, String tierName, String status,
                                  LocalDate startDate, LocalDate endDate) {
}
