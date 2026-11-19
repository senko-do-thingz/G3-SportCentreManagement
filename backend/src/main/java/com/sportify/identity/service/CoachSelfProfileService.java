package com.sportify.identity.service;

import com.sportify.identity.dto.CoachSelfCertificationCreateRequest;
import com.sportify.identity.dto.CoachSelfProfileResponse;
import com.sportify.identity.dto.CoachSelfProfileUpdateRequest;
import com.sportify.identity.entity.UserAccount;

import java.util.List;

// Dich vu tu quan ly ho so cong khai va chung chi danh cho Coach
public interface CoachSelfProfileService {

    CoachSelfProfileResponse getMyProfile(UserAccount actor);

    List<CoachSelfProfileResponse.CertificationOption> getCertificationOptions(UserAccount actor);

    CoachSelfProfileResponse updateMyProfile(CoachSelfProfileUpdateRequest request, UserAccount actor);

    CoachSelfProfileResponse uploadMyPhoto(byte[] data, UserAccount actor);

    CoachSelfProfileResponse addCertification(CoachSelfCertificationCreateRequest request, UserAccount actor);

    CoachSelfProfileResponse removeCertification(Long certificationId, UserAccount actor);
}
