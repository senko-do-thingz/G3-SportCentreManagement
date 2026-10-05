package com.sportify.catalog.service;

import com.sportify.catalog.dto.PackageRegistrationRequest;
import com.sportify.catalog.dto.PackageRegistrationResponse;
import com.sportify.catalog.dto.SportPackageCreateRequest;
import com.sportify.catalog.dto.SportPackageResponse;
import com.sportify.identity.entity.UserAccount;

import java.util.List;

public interface SportPackageService {
    List<SportPackageResponse> getAllActivePackages();
    List<SportPackageResponse> getPackagesBySport(Long sportId);
    SportPackageResponse createPackage(SportPackageCreateRequest request, UserAccount manager);
    PackageRegistrationResponse registerPackage(PackageRegistrationRequest request, UserAccount actor);
    PackageRegistrationResponse activateRegistration(Long registrationId, UserAccount actor);
    List<PackageRegistrationResponse> getMyRegistrations(UserAccount currentUser);
    List<PackageRegistrationResponse> getMemberRegistrations(Long memberId);
}
