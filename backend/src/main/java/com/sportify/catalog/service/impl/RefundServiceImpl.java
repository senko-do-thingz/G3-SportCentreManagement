package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.RefundCreateRequest;
import com.sportify.catalog.dto.RefundQuoteResponse;
import com.sportify.catalog.dto.RefundResponse;
import com.sportify.catalog.dto.RefundReviewRequest;
import com.sportify.catalog.entity.BookingStatus;
import com.sportify.catalog.entity.CheckInResult;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.RefundRequest;
import com.sportify.catalog.entity.RefundStatus;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.repository.BookingRepository;
import com.sportify.catalog.repository.BookingRepository.RefundableBookingRow;
import com.sportify.catalog.repository.ClassSessionRepository;
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.RefundRequestRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.catalog.service.RefundService;
import com.sportify.core.audit.AuditAction;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditService;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.common.SecurityUtils;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.entity.InvoiceItemType;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.InvoiceLineRepository;
import com.sportify.payment.repository.PaymentRepository;
import com.sportify.payment.service.PaymentRefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Lock order (same rule as BE-18): every path that changes a payment and the registration it paid for locks the
 * payment row first ({@link PaymentRepository#findByIdForUpdate}) and only then reads the registration. The payment id
 * is read as a single value ({@link SportPackageRegistrationRepository#findPaymentIdById}) so no registration entity is
 * loaded before the lock.
 *
 * Refund completion also cancels bookings and releases seats, so it joins the BE-08 and BE-10 order for sessions
 * (session, then its bookings, then the package registration). The full order of completion is:
 * payment, refund_request, class_session rows in ascending id order, their booking rows, sport_package_registration.
 * No booking, session or check-in path locks a payment or a refund request, so the two orders cannot form a cycle.
 * booked_count is changed only by an atomic UPDATE while the session row lock is held (BE-21 review P1-02).
 */
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    /** Refund statuses that use up the payment: only a REJECTED request lets the member ask again. */
    static final Set<RefundStatus> LIVE_STATUSES = Set.of(RefundStatus.PENDING, RefundStatus.APPROVED, RefundStatus.COMPLETED);
    static final String ENTITY_TYPE = "REFUND_REQUEST";
    private static final int TEXT_MAX = 255;

    private final RefundRequestRepository refundRequestRepository;
    private final SportPackageRegistrationRepository registrationRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentRefundService paymentRefundService;
    private final BookingRepository bookingRepository;
    private final ClassSessionRepository sessionRepository;
    private final MemberCardRepository memberCardRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final AuditService auditService;
    private final CodeFormatter codeFormatter;
    private final Clock clock;

    /** What a refund of one registration would be, computed by {@link #prepare}. */
    private record RefundBasis(SportPackageRegistration registration, Payment payment,
                               List<RefundableBookingRow> upcomingBookings, RefundQuoteResponse quote) {
    }

    @Override
    @Transactional(readOnly = true)
    public RefundQuoteResponse quoteRefund(Long registrationId, UserAccount actor) {
        return prepare(registrationId, actor, false).quote();
    }

    @Override
    @Transactional
    public RefundResponse submitRefund(RefundCreateRequest request, UserAccount actor) {
        RefundBasis basis = prepare(request.getPackageRegistrationId(), actor, true);
        Payment payment = basis.payment();
        if (basis.quote().getUnusedSessions() <= 0) {
            throw new BusinessRuleException("Cannot refund package with zero remaining sessions");
        }

        BigDecimal amountRequested = request.getAmountRequested() != null
                ? request.getAmountRequested()
                : basis.quote().getSuggestedAmount();
        if (amountRequested == null || amountRequested.signum() <= 0) {
            throw new BusinessRuleException("Amount requested must be greater than zero");
        }
        if (amountRequested.compareTo(payment.getAmount()) > 0) {
            throw new BusinessRuleException("Amount requested " + amountRequested.toPlainString()
                    + " cannot exceed the payment amount " + payment.getAmount().toPlainString());
        }

        long seq = refundRequestRepository.getNextRefundCodeSequence();
        String refundCode = codeFormatter.formatRefundCode(seq);

        RefundRequest refund = RefundRequest.builder()
                .refundCode(refundCode)
                .payment(payment)
                .packageRegistration(basis.registration())
                .member(basis.registration().getMember())
                .amountRequested(amountRequested.setScale(2, RoundingMode.HALF_UP))
                .reason(request.getReason())
                .status(RefundStatus.PENDING)
                .requestedBy(actor)
                .build();

        // Flush now so a concurrent second request hits ux_refund_request_payment_live inside this call (409).
        refund = refundRequestRepository.saveAndFlush(refund);
        return mapToRefundResponse(refund);
    }

    @Override
    @Transactional
    public RefundResponse reviewRefund(Long refundId, RefundReviewRequest request, UserAccount manager) {
        Long paymentId = refundRequestRepository.findPaymentIdById(refundId).orElse(null);
        Payment payment = paymentId != null ? lockPayment(paymentId) : null;
        RefundRequest refund = lockRefund(refundId);

        if (refund.getStatus() != RefundStatus.PENDING) {
            throw new BusinessRuleException("Refund request has already been reviewed");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        refund.setReviewedBy(manager);
        refund.setReviewedAt(now);
        refund.setManagerNote(request.getManagerNote());

        if (Boolean.TRUE.equals(request.getApproved())) {
            BigDecimal approvedAmount = request.getAmountApproved() != null
                    ? request.getAmountApproved()
                    : refund.getAmountRequested();
            if (approvedAmount == null || approvedAmount.signum() <= 0) {
                throw new BusinessRuleException("Amount approved must be greater than zero");
            }
            if (payment != null) {
                requirePaymentOfRegistration(payment, refund.getPackageRegistration());
            }
            if (payment != null && approvedAmount.compareTo(payment.getAmount()) > 0) {
                throw new BusinessRuleException("Amount approved " + approvedAmount.toPlainString()
                        + " cannot exceed the payment amount " + payment.getAmount().toPlainString());
            }
            refund.setAmountApproved(approvedAmount);
            refund.setStatus(RefundStatus.APPROVED);

            // Mark the package registration as REFUNDED upon refund approval
            SportPackageRegistration reg = refund.getPackageRegistration();
            reg.setStatus(PackageRegistrationStatus.REFUNDED);
            reg.setCancelledAt(now);
            reg.setCancelReason("Refund approved: " + refund.getRefundCode());
            registrationRepository.save(reg);
        } else {
            refund.setStatus(RefundStatus.REJECTED);
        }

        refund = refundRequestRepository.save(refund);
        return mapToRefundResponse(refund);
    }

    /**
     * One transaction: payment REFUNDED, its invoices REFUNDED, registration REFUNDED, upcoming CONFIRMED bookings
     * paid by the registration CANCELLED (their seats released), refund COMPLETED, REFUND_COMPLETED activity log.
     * Any failure rolls all of it back.
     */
    @Override
    @Transactional
    public RefundResponse completeRefund(Long refundId, UserAccount actor) {
        Long paymentId = refundRequestRepository.findPaymentIdById(refundId).orElse(null);
        Payment lockedPayment = paymentId != null ? lockPayment(paymentId) : null;
        RefundRequest refund = lockRefund(refundId);

        if (refund.getStatus() != RefundStatus.APPROVED) {
            throw new BusinessRuleException("Only approved refunds can be completed");
        }
        SportPackageRegistration reg = refund.getPackageRegistration();
        if (lockedPayment != null) {
            requirePaymentOfRegistration(lockedPayment, reg);
        }

        BigDecimal amount = refund.getAmountApproved() != null ? refund.getAmountApproved() : refund.getAmountRequested();
        Payment payment = null;
        if (paymentId != null) {
            payment = paymentRefundService.markRefunded(paymentId, refund.getRefundCode(), amount);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        // Sessions and bookings before the registration row: same order as BE-08 and BE-10.
        List<Long> cancelled = cancelUpcomingBookings(reg, refund.getRefundCode(), actor, now);

        if (reg.getStatus() != PackageRegistrationStatus.REFUNDED) {
            reg.setStatus(PackageRegistrationStatus.REFUNDED);
            if (reg.getCancelledAt() == null) {
                reg.setCancelledAt(now);
            }
            if (reg.getCancelReason() == null) {
                reg.setCancelReason("Refund completed: " + refund.getRefundCode());
            }
            registrationRepository.save(reg);
        }

        refund.setStatus(RefundStatus.COMPLETED);
        refund = refundRequestRepository.save(refund);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("amount", amount);
        details.put("paymentId", paymentId);
        details.put("paymentCode", payment != null ? payment.getPaymentCode() : null);
        details.put("registrationId", reg.getId());
        details.put("registrationCode", reg.getRegistrationCode());
        details.put("cancelledBookingIds", cancelled);
        auditService.record(AuditEvent.builder()
                .actor(actor)
                .action(AuditAction.REFUND_COMPLETED)
                .entityType(ENTITY_TYPE)
                .entityId(refund.getId())
                .entityCode(refund.getRefundCode())
                .summary(truncate("Refund " + refund.getRefundCode() + " completed: " + amount.toPlainString()
                        + (payment != null ? ", payment " + payment.getPaymentCode() + " REFUNDED" : "")
                        + ", " + cancelled.size() + " upcoming booking(s) cancelled"))
                .details(details)
                .build());

        RefundResponse response = mapToRefundResponse(refund);
        response.setCancelledBookings(cancelled.size());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponse> getPendingRefunds() {
        return refundRequestRepository.findByStatusOrderByCreatedAtAsc(RefundStatus.PENDING).stream()
                .map(this::mapToRefundResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponse> getMyRefunds(UserAccount currentUser) {
        return getMemberRefunds(currentUser.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponse> getMemberRefunds(Long memberId) {
        return refundRequestRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(this::mapToRefundResponse)
                .collect(Collectors.toList());
    }

    /**
     * Checks shared by quote and submit, in this order: registration exists (404), a member owns it (403), it has a
     * payment (409 PAYMENT_NOT_REFUNDABLE), the payment pays only for this registration (409
     * PAYMENT_REGISTRATION_MISMATCH, see {@link #requirePaymentOfRegistration}), the payment has no live refund and is not REFUNDED (409
     * PAYMENT_ALREADY_REFUNDED), the payment is SUCCESS (409 PAYMENT_NOT_REFUNDABLE) and the registration is ACTIVE
     * (400). The already-refunded check comes before the registration status check, because approving a refund already
     * moves the registration to REFUNDED. With every session used the quote answers 0.00; submit then refuses (400).
     *
     * @param lock true to lock the payment row (submit), false for a plain read (quote)
     */
    private RefundBasis prepare(Long registrationId, UserAccount actor, boolean lock) {
        if (registrationId == null) {
            throw new BusinessRuleException("Package registration ID is required");
        }
        Long paymentId = registrationRepository.findPaymentIdById(registrationId).orElse(null);
        Payment payment = null;
        if (paymentId != null) {
            payment = lock ? lockPayment(paymentId) : paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment not found for id: " + paymentId));
        }
        SportPackageRegistration reg = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Package registration not found for id: " + registrationId));

        if (SecurityUtils.isMember(actor) && !reg.getMember().getId().equals(actor.getId())) {
            throw new AccessDeniedException("Cannot request refund for another member's package registration");
        }

        if (payment == null) {
            throw new ConflictException("Package registration " + reg.getRegistrationCode()
                    + " has no payment, so it cannot be refunded", PaymentRefundService.CODE_PAYMENT_NOT_REFUNDABLE);
        }
        requirePaymentOfRegistration(payment, reg);
        if (payment.getPaymentStatus() == PaymentStatus.REFUNDED
                || refundRequestRepository.existsByPaymentIdAndStatusIn(payment.getId(), LIVE_STATUSES)) {
            throw new ConflictException("Payment " + payment.getPaymentCode()
                    + " already has a refund; a payment can be refunded only once",
                    PaymentRefundService.CODE_PAYMENT_ALREADY_REFUNDED);
        }
        if (payment.getPaymentStatus() != PaymentStatus.SUCCESS) {
            throw new ConflictException("Payment " + payment.getPaymentCode() + " is " + payment.getPaymentStatus()
                    + ", only a SUCCESS payment can be refunded", PaymentRefundService.CODE_PAYMENT_NOT_REFUNDABLE);
        }

        if (reg.getStatus() != PackageRegistrationStatus.ACTIVE) {
            throw new BusinessRuleException("Only active package registrations can be refunded");
        }

        int total = reg.getTotalSessions() != null ? reg.getTotalSessions() : 0;
        if (total <= 0) {
            throw new BusinessRuleException("Package registration " + reg.getRegistrationCode() + " has no sessions to refund");
        }
        List<RefundableBookingRow> upcoming = findUpcomingBookings(reg.getId());
        int remaining = reg.getRemainingSessions() != null ? Math.max(reg.getRemainingSessions(), 0) : 0;
        int unused = Math.min(remaining + upcoming.size(), total);

        BigDecimal paid = reg.getPaidAmount() != null ? reg.getPaidAmount() : payment.getAmount();
        BigDecimal suggested = proRated(paid, unused, total).min(payment.getAmount());

        RefundQuoteResponse quote = RefundQuoteResponse.builder()
                .registrationId(reg.getId())
                .registrationCode(reg.getRegistrationCode())
                .packageName(reg.getSportPackage() != null ? reg.getSportPackage().getName() : null)
                .paymentId(payment.getId())
                .paymentCode(payment.getPaymentCode())
                .paymentMethod(payment.getPaymentMethod())
                .paymentAmount(payment.getAmount())
                .paidAmount(paid)
                .totalSessions(total)
                .remainingSessions(remaining)
                .upcomingBookings(upcoming.size())
                .unusedSessions(unused)
                .usedSessions(total - unused)
                .suggestedAmount(suggested)
                .build();
        return new RefundBasis(reg, payment, upcoming, quote);
    }

    /** paid * unused / total, rounded half up to 2 decimals. Multiplying first keeps the result exact for whole fractions. */
    static BigDecimal proRated(BigDecimal paid, int unused, int total) {
        return paid.multiply(BigDecimal.valueOf(unused))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    /**
     * BE-21 review P1-03. The refund reaches the payment through sport_package_registration.payment_id, the only
     * supported link from a package registration to its payment. This also checks the other links a payment can have
     * (member_card.payment_id and the invoice lines of the payment, whose item_type and item_reference_id name what
     * they bill), so that a payment is never refunded, and its invoices marked REFUNDED, through a registration it
     * does not pay for alone. 409 PAYMENT_REGISTRATION_MISMATCH when: the registration's payment_id is not this
     * payment; the payment belongs to another member; a membership card is paid by the same payment; or an invoice
     * line of the payment bills something other than this registration.
     */
    private void requirePaymentOfRegistration(Payment payment, SportPackageRegistration reg) {
        Long paymentMemberId = payment.getMember() != null ? payment.getMember().getId() : null;
        Long registrationMemberId = reg.getMember() != null ? reg.getMember().getId() : null;
        boolean linked = reg.getPayment() != null && Objects.equals(reg.getPayment().getId(), payment.getId());
        if (!linked
                || paymentMemberId == null
                || !paymentMemberId.equals(registrationMemberId)
                || memberCardRepository.existsByPaymentId(payment.getId())
                || invoiceLineRepository.countPaymentLinesForOtherItems(payment.getId(),
                        InvoiceItemType.SPORT_PACKAGE, reg.getId()) > 0) {
            throw new ConflictException("Payment " + payment.getPaymentCode()
                    + " does not pay only for package registration " + reg.getRegistrationCode()
                    + ", so it cannot be refunded through it",
                    PaymentRefundService.CODE_PAYMENT_REGISTRATION_MISMATCH);
        }
    }

    /** CONFIRMED bookings of the registration on sessions not started yet, without an allowed check-in. One query. */
    private List<RefundableBookingRow> findUpcomingBookings(Long registrationId) {
        return bookingRepository.findRefundableUpcomingBookings(registrationId, BookingStatus.CONFIRMED,
                CheckInResult.ALLOWED, LocalDate.now(clock), LocalTime.now(clock));
    }

    /**
     * Cancels the upcoming bookings paid by a refunded registration and releases their seats (BE-21 review P1-02).
     * Sessions are not given back to the registration: it is REFUNDED, and the refund amount already counts these
     * bookings as unused.
     *
     * Lock order (BE-08, BE-10): the session rows are locked first, in ascending id order, with the same
     * {@link ClassSessionRepository#findForUpdate} used by booking, booking cancellation and session cancellation.
     * The bookings are then read again under those locks, so a booking cancelled meanwhile is skipped, and each
     * session's bookings are cancelled with one conditional UPDATE. booked_count is released with one atomic UPDATE
     * per session, never by writing back a value read earlier, so no seat change is lost.
     *
     * @return ids of the cancelled bookings
     */
    private List<Long> cancelUpcomingBookings(SportPackageRegistration reg, String refundCode, UserAccount actor,
                                              LocalDateTime now) {
        List<RefundableBookingRow> candidates = findUpcomingBookings(reg.getId());
        if (candidates.isEmpty()) {
            return List.of();
        }
        SortedSet<Long> sessionIds = new TreeSet<>();
        candidates.forEach(row -> sessionIds.add(row.getSessionId()));
        for (Long sessionId : sessionIds) {
            sessionRepository.findForUpdate(sessionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Class session not found for id: " + sessionId));
        }

        TreeMap<Long, List<Long>> bookingsBySession = new TreeMap<>();
        for (RefundableBookingRow row : findUpcomingBookings(reg.getId())) {
            if (!sessionIds.contains(row.getSessionId())) {
                // A new booking on a session that is not locked; locking it now would break the ascending order.
                throw bookingsChanged(reg);
            }
            bookingsBySession.computeIfAbsent(row.getSessionId(), id -> new ArrayList<>()).add(row.getBookingId());
        }

        String reason = truncate("Refund completed: " + refundCode);
        List<Long> cancelled = new ArrayList<>();
        for (Map.Entry<Long, List<Long>> entry : bookingsBySession.entrySet()) {
            Long sessionId = entry.getKey();
            List<Long> bookingIds = entry.getValue();
            int count = bookingRepository.cancelConfirmedSessionBookings(bookingIds, sessionId,
                    actor != null ? actor.getId() : null, reason, now);
            if (count != bookingIds.size()) {
                // Only a check-in recorded between the read and the update can get here; roll everything back.
                throw bookingsChanged(reg);
            }
            sessionRepository.releaseSeats(sessionId, count, now);
            cancelled.addAll(bookingIds);
        }
        return cancelled;
    }

    private static ConflictException bookingsChanged(SportPackageRegistration reg) {
        return new ConflictException("The upcoming bookings of package registration " + reg.getRegistrationCode()
                + " changed while the refund was being completed; please try again",
                PaymentRefundService.CODE_REFUND_BOOKINGS_CHANGED);
    }

    private Payment lockPayment(Long paymentId) {
        return paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for id: " + paymentId));
    }

    private RefundRequest lockRefund(Long refundId) {
        if (refundId == null) {
            throw new BusinessRuleException("Refund ID cannot be null");
        }
        return refundRequestRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund request not found for id: " + refundId));
    }

    private static String truncate(String value) {
        return value == null || value.length() <= TEXT_MAX ? value : value.substring(0, TEXT_MAX);
    }

    private RefundResponse mapToRefundResponse(RefundRequest r) {
        Payment payment = r.getPayment();
        return RefundResponse.builder()
                .id(r.getId())
                .refundCode(r.getRefundCode())
                .packageRegistrationId(r.getPackageRegistration().getId())
                .paymentId(payment != null ? payment.getId() : null)
                .paymentCode(payment != null ? payment.getPaymentCode() : null)
                .registrationCode(r.getPackageRegistration().getRegistrationCode())
                .memberId(r.getMember().getId())
                .memberCode(r.getMember().getMemberCode())
                .memberFullName(r.getMember().getUserAccount().getFullName())
                .amountRequested(r.getAmountRequested())
                .amountApproved(r.getAmountApproved())
                .reason(r.getReason())
                .status(r.getStatus())
                .requestedById(r.getRequestedBy().getId())
                .requestedByName(r.getRequestedBy().getFullName())
                .reviewedById(r.getReviewedBy() != null ? r.getReviewedBy().getId() : null)
                .reviewedByName(r.getReviewedBy() != null ? r.getReviewedBy().getFullName() : null)
                .reviewedAt(r.getReviewedAt())
                .managerNote(r.getManagerNote())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
