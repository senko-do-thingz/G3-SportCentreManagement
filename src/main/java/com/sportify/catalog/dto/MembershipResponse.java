package com.sportify.catalog.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class MembershipResponse {
    private Long id;
    private String registrationCode;
    private Long planId;
    private String planName;
    private String registrationType;
    private String channel;
    private String status;
    private BigDecimal priceAmount;
    private Integer durationDays;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<String> sports;
}
