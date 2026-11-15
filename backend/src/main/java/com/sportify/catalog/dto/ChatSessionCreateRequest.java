package com.sportify.catalog.dto;

import jakarta.validation.constraints.Size;

public record ChatSessionCreateRequest(
        @Size(max = 200, message = "Title must not exceed 200 characters")
        String title
) {}
