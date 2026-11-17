package com.sportify.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for a user (such as a Member) confirming self-deletion of their account.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeleteAccountRequest {
    @NotBlank(message = "Password is required to confirm account deletion")
    private String password;
}
