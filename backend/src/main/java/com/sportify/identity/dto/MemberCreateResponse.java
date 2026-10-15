package com.sportify.identity.dto;

public record MemberCreateResponse(Long userId, String memberCode, String fullName, String phone,
        String email, String status, String temporaryPassword) {
    @Override
    public String toString() {
        return "MemberCreateResponse[userId=" + userId + ", memberCode=" + memberCode + "]";
    }
}
