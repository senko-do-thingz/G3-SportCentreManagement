package com.sportify.identity.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MemberSearchResponse {
    private Long userId;
    private String memberCode;
    private String fullName;
    private String email;
    private String phone;
    private String currentLevel;
}
