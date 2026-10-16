package com.sportify.identity.dto;

import com.sportify.catalog.dto.SportSummaryResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private Long id;
    private String email;
    private String fullName;
    private String phone;
    private String role;
    private String status;
    private LocalDate dateOfBirth;
    private String address;
    private String mainGoal;
    private String currentLevel;
    private String memberCode;
    private ProfileCardResponse membershipCard;
    private List<SportSummaryResponse> sports;
}
