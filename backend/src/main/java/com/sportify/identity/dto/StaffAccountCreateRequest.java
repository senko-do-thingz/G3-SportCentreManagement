package com.sportify.identity.dto;

import com.sportify.core.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Create a staff account (RECEPTIONIST, COACH or MANAGER). MEMBER accounts are not created here.
 * {@code sportIds} is required for a COACH (the first id becomes the primary sport) and must be empty otherwise.
 */
public record StaffAccountCreateRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Full name must be at most 100 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email is invalid")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        @NotBlank(message = "Phone is required")
        @Size(max = 20, message = "Phone must be at most 20 characters")
        @Pattern(regexp = "^\\+?\\d{8,15}$", message = "Phone must be 8-15 digits with optional leading +")
        String phone,

        @NotBlank(message = "Temporary password is required")
        @StrongPassword
        String temporaryPassword,

        @NotBlank(message = "Role is required")
        @Size(max = 20, message = "Role must be at most 20 characters")
        String role,

        @Size(max = 20, message = "At most 20 sports can be assigned")
        List<@NotNull(message = "Sport id must not be null") Long> sportIds
) {
}
