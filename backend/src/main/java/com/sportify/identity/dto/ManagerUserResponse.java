package com.sportify.identity.dto;

import java.time.LocalDateTime;

/**
 * One row of the manager user list. Never contains the password hash.
 */
public record ManagerUserResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String role,
        String status,
        LocalDateTime createdAt
) {
}
