package com.sportify.catalog.service;

import com.sportify.catalog.dto.PackageRegistrationRequest;
import com.sportify.catalog.dto.PackageRegistrationResponse;
import com.sportify.catalog.dto.SportPackageCreateRequest;
import com.sportify.catalog.dto.SportPackageResponse;
import com.sportify.catalog.dto.SportPackageUpdateRequest;
import com.sportify.catalog.entity.TrainingFormat;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.entity.PaymentMethod;

import java.util.List;

public interface SportPackageService {
    List<SportPackageResponse> getAllActivePackages();
    List<SportPackageResponse> getPackagesBySport(Long sportId);
    List<SportPackageResponse> getManagerPackages(Long sportId, TrainingFormat format, Boolean active);
    SportPackageResponse createPackage(SportPackageCreateRequest request, UserAccount manager);
    SportPackageResponse updatePackage(Long id, SportPackageUpdateRequest request, UserAccount actor);
    PackageRegistrationResponse registerPackage(PackageRegistrationRequest request, UserAccount actor);
    PackageRegistrationResponse activateRegistration(Long registrationId, UserAccount actor);
    PackageRegistrationResponse activateRegistration(Long registrationId, UserAccount actor, PaymentMethod actualMethod);
    PackageRegistrationResponse cancelRegistration(Long registrationId, UserAccount actor);

    /**
     * Same as {@link #cancelRegistration(Long, UserAccount)}, but stores {@code reason} as the registration
     * cancel reason when it is not blank (used when the front desk marks the payment FAILED).
     */
    PackageRegistrationResponse cancelRegistration(Long registrationId, UserAccount actor, String reason);

    /**
     * Activation step only: PENDING_PAYMENT -> ACTIVE and the payment SUCCESS, without the invoice and the
     * PAYMENT_CONFIRMED log. Must run inside a transaction whose caller then calls
     * PaymentVerificationService.recordPaymentConfirmed. {@link #activateRegistration} does both.
     */
    PackageRegistrationResponse activateRegistrationForPaymentConfirmation(Long registrationId, UserAccount actor,
                                                                           PaymentMethod actualMethod);
    List<PackageRegistrationResponse> getMyRegistrations(UserAccount currentUser);
    List<PackageRegistrationResponse> getMemberRegistrations(Long memberId);
}
