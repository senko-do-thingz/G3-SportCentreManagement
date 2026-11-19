package com.sportify.identity.dto;

import java.time.LocalDate;
import java.util.List;

// Thong tin ho so huan luyen vien tu quan ly: gom thong tin cong khai, chung chi hien tai va danh sach lua chon chung chi cua trung tam
public record CoachSelfProfileResponse(
        Long userId,
        String fullName,
        String email,
        String avatarUrl,
        String headline,
        String bio,
        Integer yearsExperience,
        String specialties,
        boolean isPublic,
        int displayOrder,
        List<TaughtSport> sports,
        List<Certification> certifications,
        List<CertificationOption> certificationOptions
) {
    public record TaughtSport(Long id, String name) { }

    public record Certification(
            Long id,
            Long sportId,
            String sportName,
            String name,
            String issuer,
            LocalDate issuedOn,
            LocalDate expiresOn,
            boolean verified
    ) { }

    public record CertificationOption(
            String key,
            String name,
            String issuer,
            Long sportId,
            String sportName
    ) { }
}
