package com.sportify.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record MemberCreateRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Full name must be at most 100 characters") String fullName,
        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^\\+?\\d{8,15}$", message = "Phone must be 8-15 digits with optional leading +") String phone,
        @NotBlank(message = "Email is required")
        @Email(message = "Email is invalid")
        @Size(max = 255, message = "Email must be at most 255 characters") String email) {
    public MemberCreateRequest {
        fullName = fullName == null ? null : fullName.trim();
        phone = phone == null ? null : phone.trim();
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
