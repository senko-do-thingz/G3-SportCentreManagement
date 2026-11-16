package com.sportify.catalog.service;

import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.repository.MemberProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ma QR check-in co dinh cho moi hoi vien.
 * Noi dung QR chinh la member code, khong co token, khong het han, khong luu anh trong DB.
 * QR chi dung de check-in: le tan quet QR vao o "Member code" va goi POST /api/v1/check-ins.
 */
@Service
@RequiredArgsConstructor
public class MemberCheckInQrService {

    private final MemberProfileRepository memberProfileRepository;

    public record MemberCheckInQr(String memberCode, String qrContent) {}

    /** Noi dung QR luon giong nhau voi cung mot member code. */
    public static String qrContent(String memberCode) {
        if (memberCode == null || memberCode.isBlank()) {
            throw new IllegalArgumentException("Member code is required");
        }
        return memberCode.trim();
    }

    @Transactional(readOnly = true)
    public MemberCheckInQr forMember(long memberUserId) {
        MemberProfile profile = memberProfileRepository.findById(memberUserId)
                .filter(p -> p.getMemberCode() != null && !p.getMemberCode().isBlank())
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));
        return new MemberCheckInQr(profile.getMemberCode(), qrContent(profile.getMemberCode()));
    }
}
