package com.sportify.catalog.service;

import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.dto.BookingCreateRequest;
import com.sportify.catalog.dto.CheckInRequest;
import com.sportify.catalog.dto.MemberCardPurchaseRequest;
import com.sportify.catalog.dto.PackageRegistrationRequest;
import com.sportify.catalog.dto.TrainingWriteRequest;
import com.sportify.catalog.entity.RegistrationChannel;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.support.SessionIdentityFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Business rules found broken by the E2E run on main c6c88c5b (report: docs/test/E2E-BUSINESS-DEFECTS.md).
 * Each test asserts the CORRECT behaviour, so it fails until the matching defect is fixed.
 * A fixed clock (Wednesday 10:00, Asia/Ho_Chi_Minh) makes the time dependent rules deterministic.
 */
@Transactional
@AutoConfigureMockMvc
@Import(E2eBusinessDefectsIntegrationTest.TimeConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class E2eBusinessDefectsIntegrationTest extends AbstractIntegrationTest {
    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final LocalDate TODAY = LocalDate.of(2027, 3, 10);
    static final LocalDateTime NOW = TODAY.atTime(10, 0);
    static final SecureRandom RANDOM = new SecureRandom();

    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary Clock e2eDefectsClock() {
            return Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        }
    }

    @Autowired BookingService bookings;
    @Autowired CheckInService checkIns;
    @Autowired TrainingWriteService training;
    @Autowired SportPackageService packages;
    @Autowired MembershipCardService cards;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private final String marker = "e2e" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private int serial;
    private long sportId;
    private long facilityId;
    private long otherFacilityId;
    private long selfPackageId;
    private UserAccount receptionist;
    private UserAccount manager;

    @BeforeEach
    void fixture() {
        sportId = jdbc.queryForObject("""
                INSERT INTO sport(code, name, venue_type, is_active) OUTPUT INSERTED.id VALUES (?, ?, 'INDOOR', 1)
                """, Long.class, code(), "E2E sport " + marker);
        facilityId = facility();
        otherFacilityId = facility();
        selfPackageId = jdbc.queryForObject("""
                INSERT INTO sport_package(code, name, sport_id, training_format, duration_days, session_count, session_minutes,
                    price_amount, is_active) OUTPUT INSERTED.id VALUES (?, ?, ?, 'SELF_TRAINING', 30, 8, 90, 100000, 1)
                """, Long.class, code(), "E2E self package " + marker, sportId);
        receptionist = account("RECEPTIONIST");
        manager = account("MANAGER");
    }

    // ------------------------------------------------------------------ Flow 2

    @Test
    @DisplayName("E2E-BUG-01 (Flow 2): a member cannot hold two confirmed bookings in overlapping time slots")
    void memberCannotBookTwoOverlappingSessions() {
        UserAccount member = member();
        long registration = registration(member, 8);
        long first = session(TODAY.plusDays(1), LocalTime.of(14, 0), LocalTime.of(15, 30), facilityId);
        long second = session(TODAY.plusDays(1), LocalTime.of(14, 0), LocalTime.of(15, 30), otherFacilityId);

        bookings.createBooking(request(first, registration), member);

        assertThatThrownBy(() -> bookings.createBooking(request(second, registration), member))
                .as("second booking in the same 14:00-15:30 slot must be rejected")
                .isInstanceOf(RuntimeException.class);
        assertThat(count("SELECT COUNT(*) FROM booking WHERE member_id = ? AND status = 'CONFIRMED'", member.getId())).isEqualTo(1);
    }

    // ------------------------------------------------------------------ Flow 4

    @Test
    @DisplayName("E2E-BUG-02 (Flow 4): a booking already marked PRESENT cannot be cancelled for a credit refund")
    void presentBookingCannotBeCancelledForCredit() {
        UserAccount member = member();
        long registration = registration(member, 7);
        long session = session(TODAY, LocalTime.of(9, 0), LocalTime.of(11, 0), facilityId); // in progress at 10:00
        long booking = confirmedBooking(session, member, registration);
        training.attendance(receptionist.getId(),
                new TrainingWriteRequest.Attendance(booking, TrainingWriteRequest.AttendanceStatus.PRESENT, null));

        assertThatThrownBy(() -> bookings.cancelBooking(booking, staff(receptionist)))
                .as("cancelling a PRESENT booking must be rejected")
                .isInstanceOf(RuntimeException.class);
        assertThat(remaining(registration)).as("credit is not refunded").isEqualTo(7);
    }

    @Test
    @DisplayName("E2E-BUG-07 (Flow 4): attendance PRESENT cannot be recorded before the session starts")
    void presentCannotBeRecordedBeforeStart() {
        UserAccount member = member();
        long registration = registration(member, 7);
        long session = session(TODAY, LocalTime.of(18, 0), LocalTime.of(19, 30), facilityId); // starts 8 hours later
        long booking = confirmedBooking(session, member, registration);

        assertThatThrownBy(() -> training.attendance(receptionist.getId(),
                new TrainingWriteRequest.Attendance(booking, TrainingWriteRequest.AttendanceStatus.PRESENT, null)))
                .isInstanceOf(RuntimeException.class);
    }

    // ------------------------------------------------------------------ Flow 1

    @Test
    @DisplayName("E2E-BUG-03 (Flow 1): front desk check-in is denied after the session has ended")
    void checkInDeniedAfterSessionEnded() {
        UserAccount member = member();
        long registration = registration(member, 7);
        long session = session(TODAY, LocalTime.of(7, 0), LocalTime.of(8, 0), facilityId); // ended at 08:00
        long booking = confirmedBooking(session, member, registration);
        CheckInRequest request = new CheckInRequest();
        request.setIdentifier(String.valueOf(member.getId()));
        request.setBookingId(booking);

        var response = checkIns.recordCheckIn(request, staff(receptionist));

        assertThat(response.getResult()).isEqualTo("DENIED");
    }

    @Test
    @DisplayName("E2E-BUG-04a (Flow 1): reception cannot sell a package to a deactivated member")
    void deskPackageSaleRejectedForInactiveMember() {
        UserAccount member = member();
        deactivate(member);
        PackageRegistrationRequest request = PackageRegistrationRequest.builder()
                .memberId(member.getId()).packageId(selfPackageId).channel(RegistrationChannel.RECEPTION)
                .paymentMethod(PaymentMethod.CASH).amountReceived(new BigDecimal("100000"))
                .expectedAmountDue(new BigDecimal("100000")).build();

        assertThatThrownBy(() -> packages.registerPackage(request, staff(receptionist))).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("E2E-BUG-04b (Flow 1): reception cannot sell a membership card to a deactivated member")
    void deskCardSaleRejectedForInactiveMember() {
        UserAccount member = member();
        deactivate(member);
        Map<String, Object> gold = jdbc.queryForMap("SELECT id, price FROM membership_card_tier WHERE code = 'GOLD'");
        BigDecimal price = (BigDecimal) gold.get("price");
        MemberCardPurchaseRequest request = MemberCardPurchaseRequest.builder()
                .memberId(member.getId()).tierId(((Number) gold.get("id")).longValue()).paymentMethod(PaymentMethod.CASH)
                .amountReceived(price).expectedAmountDue(price).build();

        assertThatThrownBy(() -> cards.purchaseCard(request, staff(receptionist))).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("E2E-BUG-05 (Flow 1): an access token issued before a password change is rejected")
    void accessTokenRevokedAfterPasswordChange() throws Exception {
        String password = "E2eStrongPass2027!";
        String newPassword = "E2eNewerPass2027!";
        UserAccount member = member();
        jdbc.update("UPDATE user_account SET password_hash = ? WHERE id = ?", passwordEncoder.encode(password), member.getId());
        em.clear();
        String token = json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", member.getEmail(), "password", password))))
                .andReturn().getResponse().getContentAsString()).path("accessToken").asText();
        assertThat(token).isNotBlank();

        int changed = mvc.perform(put("/api/v1/users/me/password").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "currentPassword", password, "newPassword", newPassword, "confirmPassword", newPassword))))
                .andReturn().getResponse().getStatus();
        assertThat(changed).as("password change succeeds").isLessThan(300);

        int reuse = mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
        assertThat(reuse).as("old token after password change").isEqualTo(401);
    }

    @Test
    @DisplayName("E2E-BUG-06 (Flow 1): a member cannot open two unpaid orders of the same package")
    void secondPendingOrderOfSamePackageRejected() {
        UserAccount member = member();
        PackageRegistrationRequest request = PackageRegistrationRequest.builder()
                .packageId(selfPackageId).paymentMethod(PaymentMethod.BANK_TRANSFER).build();

        packages.registerPackage(request, member);

        assertThatThrownBy(() -> packages.registerPackage(request, member)).isInstanceOf(RuntimeException.class);
        assertThat(count("SELECT COUNT(*) FROM sport_package_registration WHERE member_id = ? AND status = 'PENDING_PAYMENT'",
                member.getId())).isEqualTo(1);
    }

    // ------------------------------------------------------------------ fixtures

    private String code() { return marker + ++serial; }

    private UserAccount account(String role) {
        return users.saveAndFlush(UserAccount.builder().email(code() + "@sportify.test").fullName(role + " e2e fixture")
                .phone(Long.toString(RANDOM.nextLong(100_000_000_000L, 1_000_000_000_000L)))
                .passwordHash("test-only-hash").role(roles.findByCode(role).orElseThrow()).status("ACTIVE").build());
    }

    private UserAccount member() {
        UserAccount account = account("MEMBER");
        jdbc.update("INSERT INTO member_profile(user_id, member_code, current_level, joined_on) VALUES (?, ?, 'BEGINNER', ?)",
                account.getId(), code(), TODAY);
        return account;
    }

    /** Reloads an actor after JDBC fixture writes so services see current rows. */
    private UserAccount staff(UserAccount actor) {
        em.flush();
        em.clear();
        return users.findById(actor.getId()).orElseThrow();
    }

    private void deactivate(UserAccount member) {
        jdbc.update("UPDATE user_account SET status = 'INACTIVE' WHERE id = ?", member.getId());
        em.clear();
    }

    private long facility() {
        return jdbc.queryForObject("""
                INSERT INTO facility(code, name, facility_type, sport_id, is_active) OUTPUT INSERTED.id VALUES (?, ?, 'COURT', ?, 1)
                """, Long.class, code(), "E2E court " + marker, sportId);
    }

    private long registration(UserAccount member, int remaining) {
        return jdbc.queryForObject("""
                INSERT INTO sport_package_registration(registration_code, member_id, package_id, channel, original_price,
                    paid_amount, total_sessions, remaining_sessions, start_date, end_date, status, created_by_user_id)
                OUTPUT INSERTED.id VALUES (?, ?, ?, 'RECEPTION', 100000, 100000, 8, ?, ?, ?, 'ACTIVE', ?)
                """, Long.class, code(), member.getId(), selfPackageId, remaining, TODAY.minusDays(2), TODAY.plusDays(28),
                manager.getId());
    }

    private long session(LocalDate date, LocalTime start, LocalTime end, long facility) {
        return jdbc.queryForObject("""
                SET NOCOUNT ON; DECLARE @created TABLE(id BIGINT);
                INSERT INTO class_session(sport_id, facility_id, training_type, session_date, start_time, end_time,
                    capacity, booked_count, status, session_code) OUTPUT INSERTED.id INTO @created
                VALUES (?, ?, 'SELF_TRAINING', ?, ?, ?, 10, 0, 'PUBLISHED', ?); SELECT id FROM @created;
                """, Long.class, sportId, facility, date, start, end, SessionIdentityFixtures.reserve(jdbc, date, "FOOTBALL"));
    }

    /** A confirmed booking that already consumed one credit and one seat, as the booking API would leave it. */
    private long confirmedBooking(long session, UserAccount member, long registration) {
        jdbc.update("UPDATE class_session SET booked_count = booked_count + 1 WHERE id = ?", session);
        return jdbc.queryForObject("""
                INSERT INTO booking(booking_code, session_id, member_id, package_registration_id, status, booked_at)
                OUTPUT INSERTED.id VALUES (?, ?, ?, ?, 'CONFIRMED', ?)
                """, Long.class, code(), session, member.getId(), registration, NOW.minusDays(1));
    }

    private BookingCreateRequest request(long session, long registration) {
        return BookingCreateRequest.builder().sessionId(session).packageRegistrationId(registration).build();
    }

    private int remaining(long registration) {
        return count("SELECT remaining_sessions FROM sport_package_registration WHERE id = ?", registration);
    }

    private int count(String sql, Object... args) {
        return Objects.requireNonNull(jdbc.queryForObject(sql, Integer.class, args));
    }
}
