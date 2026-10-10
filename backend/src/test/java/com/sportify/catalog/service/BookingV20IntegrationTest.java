package com.sportify.catalog.service;

import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.dto.BookingCreateRequest;
import com.sportify.catalog.dto.BookingResponse;
import com.sportify.catalog.entity.Booking;
import com.sportify.catalog.entity.BookingStatus;
import com.sportify.catalog.entity.Sport;
import com.sportify.catalog.entity.SportClass;
import com.sportify.catalog.repository.BookingRepository;
import com.sportify.catalog.repository.SportClassRepository;
import com.sportify.core.exception.ConflictException;
import com.sportify.identity.entity.CoachProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test verifying V20 schema mappings and business behaviors against SQL Server:
 * - Cancel booking tracks cancelledBy actor (receptionist, member, manager)
 * - SportClass persists with and without CoachProfile
 * - Duplicate booking attempts throw ConflictException (via application check and index fallback)
 * - Cancel then book again is allowed by the partial unique index ux_booking_active
 */
@Transactional
class BookingV20IntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private SportClassRepository sportClassRepository;

    @Autowired
    private UserRepository userRepository;

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "MEMBER", "MANAGER"})
    @DisplayName("Test A: Cancel booking tracks cancelledBy and response fields for given role")
    void cancelBooking_TracksCancelledBy_ForRoles(String roleCode) {
        long memberId = createMember("member_a_" + roleCode.toLowerCase() + "_" + UUID.randomUUID() + "@test.com",
                "Member Test", "MEM-" + UUID.randomUUID().toString().substring(0, 4));
        long sportId = getSportId("BADMINTON");
        long sessionId = createSession(sportId, LocalDate.now().plusDays(5), 10, 1);
        String bookingCode = "BK-" + UUID.randomUUID().toString().substring(0, 8);
        long bookingId = insertBooking(bookingCode, sessionId, memberId, "CONFIRMED");

        UserAccount actor;
        if ("MEMBER".equals(roleCode)) {
            actor = userRepository.findById(memberId).orElseThrow();
        } else {
            long actorId = createUser("actor_" + roleCode.toLowerCase() + "_" + UUID.randomUUID() + "@test.com",
                    "Staff " + roleCode, roleCode);
            actor = userRepository.findById(actorId).orElseThrow();
        }

        BookingResponse response = bookingService.cancelBooking(bookingId, actor);

        entityManager.flush();
        entityManager.clear();

        Booking reloaded = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(reloaded.getCancelledBy()).isNotNull();
        assertThat(reloaded.getCancelledBy().getId()).isEqualTo(actor.getId());
        assertThat(reloaded.getCancelledBy().getFullName()).isEqualTo(actor.getFullName());
        assertThat(reloaded.getCancelledBy().getRole().getCode()).isEqualTo(roleCode);

        assertThat(response.getCancelledByName()).isEqualTo(actor.getFullName());
        assertThat(response.getCancelledByRole()).isEqualTo(roleCode);
    }

    @Test
    @DisplayName("Test B: Persist SportClass with and without coach")
    void sportClass_PersistWithAndWithoutCoach_Succeeds() {
        long coachUserId = createCoach("coach_b_" + UUID.randomUUID() + "@test.com", "Coach Alice");
        CoachProfile coach = entityManager.find(CoachProfile.class, coachUserId);
        assertThat(coach).isNotNull();

        long sportId = getSportId("BADMINTON");
        Sport sport = entityManager.find(Sport.class, sportId);
        assertThat(sport).isNotNull();

        SportClass classWithCoach = SportClass.builder()
                .code("CLS-COACH-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Badminton Advanced")
                .sport(sport)
                .coach(coach)
                .level("ADVANCED")
                .maxMembers(16)
                .isActive(true)
                .build();
        sportClassRepository.save(classWithCoach);

        SportClass classWithoutCoach = SportClass.builder()
                .code("CLS-NOCOACH-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Badminton Beginner")
                .sport(sport)
                .coach(null)
                .level("BEGINNER")
                .maxMembers(20)
                .isActive(true)
                .build();
        sportClassRepository.save(classWithoutCoach);

        entityManager.flush();
        entityManager.clear();

        SportClass reloadedWithCoach = sportClassRepository.findById(classWithCoach.getId()).orElseThrow();
        assertThat(reloadedWithCoach.getCoach()).isNotNull();
        assertThat(reloadedWithCoach.getCoach().getId()).isEqualTo(coachUserId);

        SportClass reloadedWithoutCoach = sportClassRepository.findById(classWithoutCoach.getId()).orElseThrow();
        assertThat(reloadedWithoutCoach.getCoach()).isNull();
    }

    @Test
    @DisplayName("Test C: Duplicate booking attempt throws ConflictException")
    void createBooking_TwiceForSameMemberAndSession_ThrowsConflictException() {
        long memberId = createMember("member_c_" + UUID.randomUUID() + "@test.com",
                "Member Charlie", "MEM-" + UUID.randomUUID().toString().substring(0, 4));
        UserAccount memberUser = userRepository.findById(memberId).orElseThrow();

        long sportId = getSportId("BADMINTON");
        long sessionId = createSession(sportId, LocalDate.now().plusDays(3), 10, 0);

        long packageId = jdbc.queryForObject(
                "SELECT id FROM sport_package WHERE sport_id = ? AND training_format = 'SELF_TRAINING' AND is_active = 1",
                Long.class, sportId);
        long adminId = jdbc.queryForObject("SELECT MIN(id) FROM user_account", Long.class);
        String regCode = "REG-" + UUID.randomUUID().toString().substring(0, 8);
        long regId = insertPackageRegistration(regCode, memberId, packageId, adminId, 10, 10);

        BookingCreateRequest request = BookingCreateRequest.builder()
                .sessionId(sessionId)
                .packageRegistrationId(regId)
                .build();

        BookingResponse firstResponse = bookingService.createBooking(request, memberUser);
        assertThat(firstResponse).isNotNull();
        assertThat(firstResponse.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        assertThatThrownBy(() -> bookingService.createBooking(request, memberUser))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Member already has a confirmed booking for this session");
    }

    @Test
    @DisplayName("Test D: Cancel then book again is allowed")
    void createBooking_CancelThenBookAgain_Allowed() {
        long memberId = createMember("member_d_" + UUID.randomUUID() + "@test.com",
                "Member Delta", "MEM-" + UUID.randomUUID().toString().substring(0, 4));
        UserAccount memberUser = userRepository.findById(memberId).orElseThrow();

        long sportId = getSportId("BADMINTON");
        long sessionId = createSession(sportId, LocalDate.now().plusDays(4), 10, 0);

        long packageId = jdbc.queryForObject(
                "SELECT id FROM sport_package WHERE sport_id = ? AND training_format = 'SELF_TRAINING' AND is_active = 1",
                Long.class, sportId);
        long adminId = jdbc.queryForObject("SELECT MIN(id) FROM user_account", Long.class);
        String regCode = "REG-" + UUID.randomUUID().toString().substring(0, 8);
        long regId = insertPackageRegistration(regCode, memberId, packageId, adminId, 10, 10);

        BookingCreateRequest request = BookingCreateRequest.builder()
                .sessionId(sessionId)
                .packageRegistrationId(regId)
                .build();

        BookingResponse firstResponse = bookingService.createBooking(request, memberUser);
        assertThat(firstResponse.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        BookingResponse cancelledResponse = bookingService.cancelBooking(firstResponse.getId(), memberUser);
        assertThat(cancelledResponse.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        BookingResponse secondResponse = bookingService.createBooking(request, memberUser);
        assertThat(secondResponse).isNotNull();
        assertThat(secondResponse.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(secondResponse.getId()).isNotEqualTo(firstResponse.getId());
    }

    // ---------------------------------------------------------------------
    // Seed Helpers
    // ---------------------------------------------------------------------

    private long createUser(String email, String fullName, String roleCode) {
        jdbc.update("""
                INSERT INTO user_account (email, password_hash, full_name, role_id, status)
                SELECT ?, 'not-a-real-hash', ?, r.id, 'ACTIVE' FROM role r WHERE r.code = ?
                """, email, fullName, roleCode);
        return jdbc.queryForObject("SELECT id FROM user_account WHERE email = ?", Long.class, email);
    }

    private long createMember(String email, String fullName, String memberCode) {
        long userId = createUser(email, fullName, "MEMBER");
        jdbc.update("INSERT INTO member_profile (user_id, member_code) VALUES (?, ?)", userId, memberCode);
        return userId;
    }

    private long createCoach(String email, String fullName) {
        long userId = createUser(email, fullName, "COACH");
        jdbc.update("""
                INSERT INTO coach_profile (user_id, headline, is_public, display_order)
                VALUES (?, 'Senior Coach', 1, 1)
                """, userId);
        return userId;
    }

    private long getSportId(String code) {
        return jdbc.queryForObject("SELECT id FROM sport WHERE code = ?", Long.class, code);
    }

    private long createSession(long sportId, LocalDate date, int capacity, int bookedCount) {
        jdbc.update("""
                INSERT INTO class_session (sport_id, session_date, start_time, end_time, training_type, capacity, booked_count, status)
                VALUES (?, ?, CAST('18:00' AS TIME), CAST('19:30' AS TIME), 'SELF_TRAINING', ?, ?, 'PUBLISHED')
                """, sportId, java.sql.Date.valueOf(date), capacity, bookedCount);
        return jdbc.queryForObject("SELECT MAX(id) FROM class_session WHERE sport_id = ?", Long.class, sportId);
    }

    private long insertBooking(String code, long sessionId, long memberId, String status) {
        jdbc.update("""
                INSERT INTO booking (booking_code, session_id, member_id, status)
                VALUES (?, ?, ?, ?)
                """, code, sessionId, memberId, status);
        return jdbc.queryForObject("SELECT id FROM booking WHERE booking_code = ?", Long.class, code);
    }

    private long insertPackageRegistration(String regCode, long memberId, long packageId, long createdByUserId, int totalSessions, int remainingSessions) {
        LocalDate today = LocalDate.now();
        jdbc.update("""
                INSERT INTO sport_package_registration (
                    registration_code, member_id, package_id, channel, original_price, discount_percentage, paid_amount,
                    total_sessions, remaining_sessions, start_date, end_date, status, created_by_user_id
                ) VALUES (
                    ?, ?, ?, 'ONLINE', 100000.00, 0, 100000.00,
                    ?, ?, ?, ?, 'ACTIVE', ?
                )
                """, regCode, memberId, packageId, totalSessions, remainingSessions,
                java.sql.Date.valueOf(today.minusDays(10)),
                java.sql.Date.valueOf(today.plusDays(30)),
                createdByUserId);
        return jdbc.queryForObject("SELECT id FROM sport_package_registration WHERE registration_code = ?", Long.class, regCode);
    }
}
