package com.sportify.catalog.service;

import com.sportify.catalog.dto.SessionDetailResponse.EligibilityReason;
import com.sportify.catalog.entity.ClassSession;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.SportPackageRegistration;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.sportify.catalog.dto.SessionDetailResponse.EligibilityReason.*;

/** Shared purchased-package rules for BE-11 reads and fresh transactional booking selection. */
public final class PackageBookingEligibility {
    private static final Comparator<SportPackageRegistration> ORDER = Comparator
            .comparing(SportPackageRegistration::getEndDate).thenComparing(SportPackageRegistration::getStartDate)
            .thenComparing(SportPackageRegistration::getId);

    private PackageBookingEligibility() { }

    public static List<SportPackageRegistration> matching(List<SportPackageRegistration> rows,
            ClassSession session, Long memberId) {
        return rows.stream().filter(r -> r.getMember() != null && Objects.equals(memberId, r.getMember().getId())
                && (r.getStatus() == PackageRegistrationStatus.ACTIVE || r.getStatus() == PackageRegistrationStatus.EXPIRED)
                && r.getSportPackage() != null && r.getSportPackage().getSport() != null
                && Objects.equals(r.getSportPackage().getSport().getId(), session.getSport().getId())
                && r.getSportPackage().getTrainingFormat() == session.getTrainingType()).toList();
    }

    public static boolean covers(SportPackageRegistration r, LocalDate date) {
        return r.getStatus() == PackageRegistrationStatus.ACTIVE
                && !date.isBefore(r.getStartDate()) && !date.isAfter(r.getEndDate());
    }

    public static List<SportPackageRegistration> usable(List<SportPackageRegistration> matching, LocalDate date) {
        return matching.stream().filter(r -> covers(r, date)).filter(r -> r.getRemainingSessions() > 0)
                .sorted(ORDER).toList();
    }

    /** Call only when no usable registration exists; preserves BE-11 set-based precedence. */
    public static EligibilityReason rejection(List<SportPackageRegistration> matching, LocalDate date) {
        if (matching.stream().anyMatch(r -> covers(r, date))) return NO_SESSIONS_LEFT;
        if (matching.isEmpty()) return NO_PACKAGE;
        if (matching.stream().anyMatch(r -> r.getStatus() == PackageRegistrationStatus.EXPIRED || r.getEndDate().isBefore(date))) {
            return PACKAGE_EXPIRED_BY_THEN;
        }
        return PACKAGE_NOT_STARTED_BY_THEN;
    }
}
