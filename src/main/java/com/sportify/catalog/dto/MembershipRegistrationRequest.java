package com.sportify.catalog.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class MembershipRegistrationRequest {
    @NotNull(message = "Plan ID is required")
    private Long planId;
    
    @NotEmpty(message = "At least one sport must be selected")
    private List<Long> sportIds;
}
