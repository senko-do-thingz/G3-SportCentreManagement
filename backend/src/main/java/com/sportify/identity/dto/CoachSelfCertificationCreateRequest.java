package com.sportify.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// Yeu cau them chung chi tu danh sach chung chi co san cua trung tam
public record CoachSelfCertificationCreateRequest(
        @NotBlank(message = "Choose a certification from the list")
        @Size(max = 150, message = "Certification name must be at most 150 characters")
        String name,

        @Size(max = 150, message = "Issuer must be at most 150 characters")
        String issuer,

        Long sportId,

        LocalDate issuedOn,

        LocalDate expiresOn
) {
}
