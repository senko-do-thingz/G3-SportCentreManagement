package com.sportify.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CheckInRequest {
    @NotBlank(message = "Identifier is required")
    private String identifier; // member_code or user_id as string
    private String note;
}
