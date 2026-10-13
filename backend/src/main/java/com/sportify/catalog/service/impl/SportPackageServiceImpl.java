package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.PackageRegistrationRequest;
import com.sportify.catalog.dto.PackageRegistrationResponse;
import com.sportify.catalog.dto.SportPackageCreateRequest;
import com.sportify.catalog.dto.SportPackageResponse;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.RegistrationChannel;
import com.sportify.catalog.entity.Sport;
import com.sportify.catalog.entity.SportPackage;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.entity.TrainingFormat;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.catalog.repository.SportPackageRepository;
import com.sportify.catalog.dto.SportPackageUpdateRequest;
import com.sportify.catalog.repository.SportPackageSpecifications;
import com.sportify.catalog.repository.SportRepository;
import com.sportify.catalog.service.MembershipCardService;
import com.sportify.catalog.service.SportPackageService;
import com.sportify.core.audit.AuditAction;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditService;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.core.common.SecurityUtils;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.MemberProfileRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SportPackageServiceImpl implements SportPackageService {

    private final SportPackageRepository sportPackageRepository;
    private final SportPackageRegistrationRepository registrationRepository;
    private final SportRepository sportRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final UserRepository userRepository;
    private final MembershipCardService membershipCardService;
    private final PaymentService paymentService;
    private final CodeFormatter codeFormatter;
    private final AuditService auditService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<SportPackageResponse> getAllActivePackages() {
        return sportPackageRepository.findByIsActiveTrue().stream()
                .map(this::mapToPackageResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SportPackageResponse> getPackagesBySport(Long sportId) {
        return sportPackageRepository.findBySportIdAndIsActiveTrue(sportId).stream()
                .map(this::mapToPackageResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SportPackageResponse> getManagerPackages(Long sportId, TrainingFormat format, Boolean active) {
        return sportPackageRepository.findAll(SportPackageSpecifications.withFilters(sportId, format, active), Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(this::mapToPackageResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SportPackageResponse updatePackage(Long id, SportPackageUpdateRequest request, UserAccount actor) {
        SportPackage pkg = sportPackageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sport package not found for id: " + id));

        if (request.getSessionMinutes() == null || (request.getSessionMinutes() != 60 && request.getSessionMinutes() != 90)) {
            throw new BusinessRuleException("Session minutes must be either 60 or 90");
        }

        BigDecimal price = request.getPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Price must be greater than zero");
        }

        if (request.getName() == null || request.getName().trim().isBlank()) {
            throw new BusinessRuleException("Package name cannot be blank");
        }

        if (request.getSessionCount() == null || request.getSessionCount() <= 0) {
            throw new BusinessRuleException("Session count must be at least 1");
        }

        if (request.getDurationDays() == null || request.getDurationDays() <= 0) {
            throw new BusinessRuleException("Duration in days must be at least 1");
        }

        if (request.getActive() == null) {
            throw new BusinessRuleException("Active flag is required");
        }

        Map<String, Object> before = snapshotPackage(pkg);

        pkg.setName(request.getName().trim());
        pkg.setPriceAmount(price);
        pkg.setSessionCount(request.getSessionCount());
        pkg.setDurationDays(request.getDurationDays());
        pkg.setSessionMinutes(request.getSessionMinutes());
        pkg.setIsActive(request.getActive());
        if (request.getDescription() != null) {
            pkg.setDescription(request.getDescription().trim());
        }

        SportPackage saved = sportPackageRepository.saveAndFlush(pkg);
        Map<String, Object> after = snapshotPackage(saved);

        auditService.record(AuditEvent.builder()
                .actor(actor)
                .action(AuditAction.PACKAGE_UPDATED)
                .entityType("SPORT_PACKAGE")
                .entityId(saved.getId())
                .entityCode(saved.getCode())
                .summary("Sport package '" + saved.getName() + "' updated")
                .details(Map.of("before", before, "after", after))
                .build());

        return mapToPackageResponse(saved);
    }

    private Map<String, Object> snapshotPackage(SportPackage p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", p.getName());
        map.put("priceAmount", p.getPriceAmount());
        map.put("sessionCount", p.getSessionCount());
        map.put("durationDays", p.getDurationDays());
        map.put("sessionMinutes", p.getSessionMinutes());
        map.put("isActive", p.getIsActive());
        map.put("description", p.getDescription());
        return map;
    }

    @Override
    @Transactional
    public SportPackageResponse createPackage(SportPackageCreateRequest request, UserAccount manager) {
        Sport sport = sportRepository.findById(request.getSportId())
                .orElseThrow(() -> new ResourceNotFoundException("Sport not found for id: " + request.getSportId()));

        SportPackage pkg = SportPackage.builder()
                .code(request.getCode())
                .name(request.getName())
                .sport(sport)
                .trainingFormat(request.getTrainingFormat())
                .durationDays(request.getDurationDays())
                .sessionCount(request.getSessionCount())
                .sessionMinutes(request.getSessionMinutes() != null ? request.getSessionMinutes() : (request.getTrainingFormat() == TrainingFormat.COACH_LED ? 90 : 60))
                .priceAmount(request.getPriceAmount())
                .description(request.getDescription())
                .isActive(true)
                .build();

        pkg = sportPackageRepository.save(pkg);
        return mapToPackageResponse(pkg);
    }

    @Override
    @Transactional
    public PackageRegistrationResponse registerPackage(PackageRegistrationRequest request, UserAccount actor) {
        Long targetMemberId;
        if (SecurityUtils.isMember(actor)) {
            targetMemberId = actor.getId();
        } else {
            targetMemberId = request.getMemberId() != null ? request.getMemberId() : actor.getId();
        }

        MemberProfile member = memberProfileRepository.findById(targetMemberId).orElse(null);
        if (member == null) {
            UserAccount targetUser = userRepository.findById(targetMemberId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetMemberId));
            if (!SecurityUtils.isMember(targetUser)) {
                throw new BusinessRuleException("Cannot create member profile for non-member user: " + targetMemberId);
            }
            Long nextSeq = memberProfileRepository.getNextMemberCode();
            String memberCode = codeFormatter.formatMemberCode(nextSeq);
            member = MemberProfile.builder()
                    .userAccount(targetUser)
                    .memberCode(memberCode)
                    .currentLevel("BEGINNER")
                    .joinedOn(LocalDate.now(clock))
                    .build();
            member = memberProfileRepository.save(member);
        }

        SportPackage pkg = sportPackageRepository.findById(request.getPackageId())
                .orElseThrow(() -> new ResourceNotFoundException("Sport package not found for id: " + request.getPackageId()));

        if (!Boolean.TRUE.equals(pkg.getIsActive())) {
            throw new BusinessRuleException("Cannot register for an inactive sport package");
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : today;
        if (startDate.isBefore(today)) {
            throw new BusinessRuleException("Start date cannot be in the past");
        }

        // A package of N days covers N calendar days including the start date (a single visit: that day only)
        LocalDate endDate = packageEnd(startDate, pkg.getDurationDays());

        // Check active membership cards for discount (single visit packages excluded)
        int discountPercentage = membershipCardService.getApplicableDiscountPercentage(member.getId(), pkg.getDurationDays());

        BigDecimal originalPrice = pkg.getPriceAmount();
        BigDecimal discountFactor = BigDecimal.valueOf(100 - discountPercentage).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal paidAmount = originalPrice.multiply(discountFactor).setScale(2, RoundingMode.HALF_UP);

        if (SecurityUtils.isMember(actor) && paidAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Payment amount must be greater than zero. Attempted package price: " + paidAmount);
        }

        long seq = registrationRepository.getNextRegistrationCodeSequence();
        String regCode = codeFormatter.formatPackageRegCode(seq);

        RegistrationChannel channel;
        PackageRegistrationStatus initialStatus;
        LocalDateTime activatedAt;

        if (SecurityUtils.isMember(actor)) {
            // For a MEMBER actor, ignore request.channel and force RegistrationChannel.ONLINE with initial status PENDING_PAYMENT
            channel = RegistrationChannel.ONLINE;
            initialStatus = PackageRegistrationStatus.PENDING_PAYMENT;
            activatedAt = null;
        } else if (SecurityUtils.isStaff(actor)) {
            // Only RECEPTIONIST and MANAGER may create ACTIVE registrations (channel RECEPTION)
            channel = request.getChannel() != null ? request.getChannel() : RegistrationChannel.RECEPTION;
            if (channel == RegistrationChannel.RECEPTION) {
                initialStatus = PackageRegistrationStatus.ACTIVE;
                activatedAt = LocalDateTime.now(clock);
            } else {
                initialStatus = PackageRegistrationStatus.PENDING_PAYMENT;
                activatedAt = null;
            }
        } else {
            channel = RegistrationChannel.ONLINE;
            initialStatus = PackageRegistrationStatus.PENDING_PAYMENT;
            activatedAt = null;
        }

        SportPackageRegistration registration = SportPackageRegistration.builder()
                .registrationCode(regCode)
                .member(member)
                .sportPackage(pkg)
                .channel(channel)
                .originalPrice(originalPrice)
                .discountPercentage(discountPercentage)
                .paidAmount(paidAmount)
                .totalSessions(pkg.getSessionCount())
                .remainingSessions(pkg.getSessionCount())
                .startDate(startDate)
                .endDate(endDate)
                .status(initialStatus)
                .activatedAt(activatedAt)
                .createdByUser(actor)
                .build();

        if (initialStatus == PackageRegistrationStatus.PENDING_PAYMENT && channel == RegistrationChannel.ONLINE) {
            Payment payment = paymentService.createPendingPayment(member, paidAmount, request.getPaymentMethod());
            registration.setPayment(payment);
        }

        registration = registrationRepository.save(registration);
        return mapToRegistrationResponse(registration);
    }

    @Override
    @Transactional
    public PackageRegistrationResponse activateRegistration(Long registrationId, UserAccount actor) {
        return activateRegistration(registrationId, actor, null);
    }

    @Override
    @Transactional
    public PackageRegistrationResponse activateRegistration(Long registrationId, UserAccount actor, PaymentMethod actualMethod) {
        SportPackageRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Package registration not found for id: " + registrationId));

        if (registration.getStatus() != PackageRegistrationStatus.PENDING_PAYMENT) {
            throw new BusinessRuleException("Cannot activate package registration with status: " + registration.getStatus() + ". Only PENDING_PAYMENT registrations can be activated.");
        }
        if (actualMethod != null && registration.getPayment() == null) {
            throw new BusinessRuleException("Cannot specify payment method for a registration with no linked payment");
        }

        LocalDate today = LocalDate.now(clock);
        if (registration.getStartDate().isBefore(today)) {
            registration.setStartDate(today);
            registration.setEndDate(packageEnd(today, registration.getSportPackage().getDurationDays()));
        }

        registration.setStatus(PackageRegistrationStatus.ACTIVE);
        registration.setActivatedAt(LocalDateTime.now(clock));
        registration = registrationRepository.save(registration);
        if (registration.getPayment() != null) {
            paymentService.markPaymentSuccess(registration.getPayment().getId(), actor, actualMethod);
        }
        return mapToRegistrationResponse(registration);
    }

    @Override
    @Transactional
    public PackageRegistrationResponse cancelRegistration(Long registrationId, UserAccount actor) {
        SportPackageRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Package registration not found for id: " + registrationId));

        boolean isStaff = SecurityUtils.isStaff(actor);
        // MemberProfile shares its primary key with UserAccount (@MapsId user_id), so member id equals user id by design.
        if (!isStaff && !registration.getMember().getId().equals(actor.getId())) {
            throw new ResourceNotFoundException("Package registration not found for id: " + registrationId);
        }

        if (registration.getStatus() != PackageRegistrationStatus.PENDING_PAYMENT) {
            throw new BusinessRuleException("Only a package registration that is waiting for payment can be cancelled.");
        }

        String actorDescription = isStaff ? "staff user id " + actor.getId() : "member user id " + actor.getId();
        String cancelReason = "Cancelled by " + actorDescription;
        registration.setStatus(PackageRegistrationStatus.CANCELLED);
        registration.setCancelledAt(LocalDateTime.now(clock));
        registration.setCancelReason(cancelReason);

        if (registration.getPayment() != null) {
            paymentService.markPaymentFailed(registration.getPayment().getId(),
                    "Payment cancelled due to package registration cancellation: " + cancelReason);
        }

        registration = registrationRepository.save(registration);
        return mapToRegistrationResponse(registration);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PackageRegistrationResponse> getMyRegistrations(UserAccount currentUser) {
        return getMemberRegistrations(currentUser.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PackageRegistrationResponse> getMemberRegistrations(Long memberId) {
        return registrationRepository.findByMemberIdOrderByStartDateDesc(memberId).stream()
                .map(this::mapToRegistrationResponse)
                .collect(Collectors.toList());
    }

    private SportPackageResponse mapToPackageResponse(SportPackage pkg) {
        return SportPackageResponse.builder()
                .id(pkg.getId())
                .code(pkg.getCode())
                .name(pkg.getName())
                .sportId(pkg.getSport().getId())
                .sportName(pkg.getSport().getName())
                .sportCode(pkg.getSport().getCode())
                .trainingFormat(pkg.getTrainingFormat())
                .durationDays(pkg.getDurationDays())
                .sessionCount(pkg.getSessionCount())
                .sessionMinutes(pkg.getSessionMinutes())
                .priceAmount(pkg.getPriceAmount())
                .description(pkg.getDescription())
                .isActive(pkg.getIsActive())
                .build();
    }

    private PackageRegistrationResponse mapToRegistrationResponse(SportPackageRegistration reg) {
        PackageRegistrationResponse.PackageRegistrationResponseBuilder builder = PackageRegistrationResponse.builder()
                .id(reg.getId())
                .registrationCode(reg.getRegistrationCode())
                .memberId(reg.getMember().getId())
                .memberCode(reg.getMember().getMemberCode())
                .memberFullName(reg.getMember().getUserAccount().getFullName())
                .packageId(reg.getSportPackage().getId())
                .packageCode(reg.getSportPackage().getCode())
                .packageName(reg.getSportPackage().getName())
                .sportName(reg.getSportPackage().getSport().getName())
                .trainingFormat(reg.getSportPackage().getTrainingFormat())
                .channel(reg.getChannel())
                .originalPrice(reg.getOriginalPrice())
                .discountPercentage(reg.getDiscountPercentage())
                .paidAmount(reg.getPaidAmount())
                .totalSessions(reg.getTotalSessions())
                .remainingSessions(reg.getRemainingSessions())
                .startDate(reg.getStartDate())
                .endDate(reg.getEndDate())
                .status(reg.getStatus())
                .activatedAt(reg.getActivatedAt())
                .createdAt(reg.getCreatedAt());

        if (reg.getPayment() != null) {
            builder.paymentId(reg.getPayment().getId())
                    .paymentCode(reg.getPayment().getPaymentCode());
        }

        return builder.build();
    }

    static LocalDate packageEnd(LocalDate startDate, int durationDays) {
        return startDate.plusDays(durationDays - 1L);
    }
}
