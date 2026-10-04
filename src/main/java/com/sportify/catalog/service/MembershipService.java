package com.sportify.catalog.service;

import com.sportify.catalog.dto.MembershipRegistrationRequest;
import com.sportify.catalog.dto.MembershipResponse;
import com.sportify.identity.entity.UserAccount;

import java.time.LocalDate;
import java.util.List;

public interface MembershipService {
    MembershipResponse registerMembership(UserAccount currentUser, MembershipRegistrationRequest request);
    MembershipResponse registerForMember(Long memberId, UserAccount currentUser, MembershipRegistrationRequest request);
    void cancelMembership(Long id, UserAccount currentUser);
    List<MembershipResponse> getMyMemberships(UserAccount currentUser);
    void activateMembership(Long id, UserAccount actor);
    void processScheduledMemberships(LocalDate today);
    void processExpiredMemberships(LocalDate today);
}
