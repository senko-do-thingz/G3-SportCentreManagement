package com.sportify.catalog.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.dto.*;
import com.sportify.catalog.entity.*;
import com.sportify.catalog.repository.*;
import com.sportify.catalog.service.BookingService;
import com.sportify.catalog.service.ManagerSessionService;
import com.sportify.catalog.service.MemberScheduleService;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.identity.entity.*;
import com.sportify.identity.repository.*;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.service.CoachPublicProfileService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;

import static com.sportify.catalog.dto.SessionDetailResponse.EligibilityReason.*;
import static com.sportify.catalog.test.ScheduleTestData.CLOCK;
import static com.sportify.catalog.test.ScheduleTestData.NOW;
import static com.sportify.catalog.test.ScheduleTestData.TODAY;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** SQL Server 2022 tests. Required in CI; compilation/discovery is not an execution pass. */
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(MemberScheduleIntegrationTest.FixedTime.class)
class MemberScheduleIntegrationTest extends AbstractIntegrationTest {
    @TestConfiguration static class FixedTime {
        @Bean @Primary Clock scheduleTestClock() { return CLOCK; }
    }
    @Autowired MemberScheduleService service;
    @Autowired CoachPublicProfileService profiles;
    @Autowired BookingService bookingService;
    @Autowired ManagerSessionService managerSessions;
    @Autowired ClassSessionRepository sessions;
    @Autowired SportRepository sports;
    @Autowired SportClassRepository classes;
    @Autowired FacilityRepository facilities;
    @Autowired SportPackageRepository packages;
    @Autowired SportPackageRegistrationRepository registrations;
    @Autowired MemberProfileRepository members;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired BookingRepository bookings;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory emf;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private final String marker = "be11" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private int serial;
    private Sport sport;
    private SportClass sportClass;
    private Facility facility;
    private UserAccount manager;
    private UserAccount member;
    private CoachProfile coach;

    @BeforeEach void setUp() {
        sport = newSport();
        manager = account("MANAGER");
        member = account("MEMBER");
        coach = coach(sport);
        facility = facilities.saveAndFlush(Facility.builder().code(code()).name("BE11 court")
                .facilityType(FacilityType.COURT).sport(sport).isActive(true).build());
        sportClass = newClass(sport, "BEGINNER");
    }

    @AfterEach void cleanUp() {
        // Only this test instance's UUID-marked rows are deleted, in foreign-key order.
        String ownUsers = "SELECT id FROM user_account WHERE email LIKE ?";
        String ownSports = "SELECT id FROM sport WHERE code LIKE ?";
        jdbc.update("DELETE FROM activity_log WHERE actor_user_id IN (" + ownUsers + ")", marker + "%");
        jdbc.update("DELETE FROM booking WHERE session_id IN (SELECT id FROM class_session WHERE sport_id IN (" + ownSports + "))", marker + "%");
        jdbc.update("DELETE FROM class_session WHERE sport_id IN (" + ownSports + ")", marker + "%");
        jdbc.update("DELETE FROM sport_package_registration WHERE registration_code LIKE ?", marker + "%");
        jdbc.update("DELETE FROM sport_package WHERE code LIKE ?", marker + "%");
        jdbc.update("DELETE FROM sport_class WHERE code LIKE ?", marker + "%");
        jdbc.update("DELETE FROM facility WHERE code LIKE ?", marker + "%");
        jdbc.update("DELETE FROM coach_certification WHERE coach_id IN (" + ownUsers + ")", marker + "%");
        jdbc.update("DELETE FROM coach_sport WHERE coach_id IN (" + ownUsers + ")", marker + "%");
        jdbc.update("DELETE FROM coach_profile WHERE user_id IN (" + ownUsers + ")", marker + "%");
        jdbc.update("DELETE FROM member_profile WHERE user_id IN (" + ownUsers + ")", marker + "%");
        jdbc.update("DELETE FROM user_account WHERE email LIKE ?", marker + "%");
        jdbc.update("DELETE FROM sport WHERE code LIKE ?", marker + "%");
    }

    private String code() { return marker + (++serial); }

    private Sport newSport() {
        return sports.saveAndFlush(Sport.builder().code(code()).name(code()).venueType(SportVenueType.OUTDOOR).isActive(true).build());
    }

    private UserAccount account(String role) {
        var account = users.saveAndFlush(UserAccount.builder().email(code() + "@example.test").fullName(code())
                .passwordHash("PrivateFixtureHash").status("ACTIVE").role(roles.findByCode(role).orElseThrow()).build());
        if (role.equals("MEMBER")) jdbc.update("INSERT INTO member_profile(user_id, member_code, current_level, joined_on) VALUES (?, ?, 'BEGINNER', ?)",
                account.getId(), code(), TODAY);
        return account;
    }

    private CoachProfile coach(Sport sport) {
        var account = account("COACH");
        jdbc.update("INSERT INTO coach_profile(user_id, primary_sport_id, headline, bio, is_public, display_order) VALUES (?, ?, ?, ?, 1, 0)",
                account.getId(), sport.getId(), "Public headline", "Public biography");
        jdbc.update("INSERT INTO coach_sport(coach_id, sport_id) VALUES (?, ?)", account.getId(), sport.getId());
        return CoachProfile.builder().id(account.getId()).userAccount(account).isPublic(true).build();
    }

    private SportClass newClass(Sport sport, String level) {
        return classes.saveAndFlush(SportClass.builder().code(code()).name(code()).sport(sport).level(level).maxMembers(30).isActive(true).build());
    }

    private ClassSession session() {
        return save(ClassSession.builder().sport(sport).sportClass(sportClass).facility(facility).coach(coach)
                .sessionDate(TODAY.plusDays(1)).startTime(LocalTime.of(11, 0)).endTime(LocalTime.NOON)
                .trainingType(TrainingFormat.COACH_LED).status(SessionStatus.PUBLISHED).capacity(10).bookedCount(0).build());
    }

    private ClassSession save(ClassSession session) { return sessions.saveAndFlush(session); }

    private SportPackageRegistration registration(UserAccount owner, Sport sport, TrainingFormat format,
            String status, LocalDate start, LocalDate end, int remaining) {
        var p = packages.saveAndFlush(SportPackage.builder().code(code()).name(code()).sport(sport).trainingFormat(format)
                .durationDays(30).sessionCount(10).sessionMinutes(90).priceAmount(BigDecimal.valueOf(100)).isActive(true).build());
        return registrations.saveAndFlush(SportPackageRegistration.builder().registrationCode(code())
                .member(members.findById(owner.getId()).orElseThrow()).sportPackage(p).channel(RegistrationChannel.RECEPTION)
                .originalPrice(BigDecimal.valueOf(100)).paidAmount(BigDecimal.valueOf(100)).discountPercentage(0)
                .totalSessions(10).remainingSessions(remaining).startDate(start).endDate(end)
                .status(PackageRegistrationStatus.valueOf(status)).createdByUser(manager).build());
    }

    private Booking booking(ClassSession s, UserAccount owner, BookingStatus status) {
        return bookings.saveAndFlush(Booking.builder().bookingCode(code()).session(s)
                .member(members.findById(owner.getId()).orElseThrow()).status(status).bookedAt(LocalDateTime.now(CLOCK)).build());
    }

    private SessionSearchQuery query() { return new SessionSearchQuery(sport.getId(), null, null, null, null, null, 0, 100); }
    private List<Long> ids(SessionSearchQuery q) { return service.listSessions(q, manager).content().stream().map(SessionResponse::id).toList(); }

    @ParameterizedTest @ValueSource(strings = {"DEFAULT", "SPORT", "FROM", "TO", "FORMAT", "LEVEL", "COACH"})
    void search_filtersInSqlAndCombinesWithPublishedFutureVisibility(String filter) {
        var expected = session();
        var decoy = session();
        switch (filter) {
            case "DEFAULT" -> decoy.setStatus(SessionStatus.DRAFT);
            case "SPORT" -> {
                var other = newSport(); decoy.setSport(other); decoy.setSportClass(newClass(other, "BEGINNER"));
                jdbc.update("INSERT INTO coach_sport(coach_id, sport_id) VALUES (?, ?)", coach.getId(), other.getId());
            }
            case "FROM" -> decoy.setSessionDate(TODAY);
            case "TO" -> decoy.setSessionDate(TODAY.plusDays(2));
            case "FORMAT" -> { decoy.setTrainingType(TrainingFormat.SELF_TRAINING); decoy.setCoach(null); }
            case "LEVEL" -> decoy.setSportClass(newClass(sport, "ADVANCED"));
            case "COACH" -> decoy.setCoach(coach(sport));
            default -> throw new IllegalStateException(filter);
        }
        save(decoy);
        var q = new SessionSearchQuery(sport.getId(), filter.equals("FROM") ? TODAY.plusDays(1) : null,
                filter.equals("TO") ? TODAY.plusDays(1) : null, filter.equals("FORMAT") ? TrainingFormat.COACH_LED : null,
                filter.equals("LEVEL") ? ClassLevel.BEGINNER : null, filter.equals("COACH") ? coach.getId() : null, 0, 100);
        assertThat(ids(q)).containsExactly(expected.getId());
    }

    @Test void allFiltersUseAndNotOrAcrossIndependentDecoys() {
        var expected = session();
        var otherSport = newSport();
        jdbc.update("INSERT INTO coach_sport(coach_id, sport_id) VALUES (?, ?)", coach.getId(), otherSport.getId());
        var wrongSport = session(); wrongSport.setSport(otherSport); wrongSport.setSportClass(newClass(otherSport, "BEGINNER")); save(wrongSport);
        var wrongFrom = session(); wrongFrom.setSessionDate(TODAY); save(wrongFrom);
        var wrongTo = session(); wrongTo.setSessionDate(TODAY.plusDays(2)); save(wrongTo);
        var wrongFormat = session(); wrongFormat.setTrainingType(TrainingFormat.SELF_TRAINING); wrongFormat.setCoach(null); save(wrongFormat);
        var wrongLevel = session(); wrongLevel.setSportClass(newClass(sport, "ADVANCED")); save(wrongLevel);
        var wrongCoach = session(); wrongCoach.setCoach(coach(sport)); save(wrongCoach);
        assertThat(ids(new SessionSearchQuery(sport.getId(), TODAY.plusDays(1), TODAY.plusDays(1),
                TrainingFormat.COACH_LED, ClassLevel.BEGINNER, coach.getId(), 0, 20))).containsExactly(expected.getId());
    }

    @Test void paginationCountsJoinedRowsOnceAndHasBoundedQueryCountAndStableTieBreaks() {
        var rows = new ArrayList<ClassSession>();
        for (int i = 0; i < 25; i++) rows.add(session());
        booking(rows.getFirst(), member, BookingStatus.CONFIRMED);
        booking(rows.getFirst(), member, BookingStatus.CANCELLED);
        // A deliberately stale cache must not change the confirmed row count in read DTOs.
        jdbc.update("UPDATE class_session SET booked_count = 99 WHERE id = ?", rows.getFirst().getId());
        var stats = emf.unwrap(SessionFactory.class).getStatistics(); stats.clear();
        var first = service.listSessions(new SessionSearchQuery(sport.getId(), null, null, null, null, null, 0, 10), manager);
        assertThat(first.totalElements()).isEqualTo(25);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.content()).extracting(SessionResponse::id).containsExactlyElementsOf(rows.subList(0, 10).stream().map(ClassSession::getId).toList());
        assertThat(first.content().getFirst().bookedCount()).isEqualTo(1);
        assertThat(first.content().getFirst().freeSeats()).isEqualTo(9);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(3);
        var next = service.listSessions(new SessionSearchQuery(sport.getId(), null, null, null, null, null, 1, 10), manager);
        var last = service.listSessions(new SessionSearchQuery(sport.getId(), null, null, null, null, null, 2, 10), manager);
        var all = new ArrayList<Long>(); first.content().forEach(r -> all.add(r.id()));
        next.content().forEach(r -> all.add(r.id())); last.content().forEach(r -> all.add(r.id()));
        assertThat(last.content()).hasSize(5);
        assertThat(all).doesNotHaveDuplicates().containsExactlyElementsOf(rows.stream().map(ClassSession::getId).toList());
    }

    @ParameterizedTest @ValueSource(strings = {"PAST_DATE", "PAST_TIME", "EXACT_START", "DRAFT", "CANCELLED", "COMPLETED",
            "INACTIVE_CLASS", "INACTIVE_FACILITY", "INACTIVE_COACH", "UNQUALIFIED_COACH", "NO_COACH", "OVERNIGHT"})
    void hiddenSessionsAreAbsentAndDirectDetailIs404(String hidden) {
        var s = session();
        switch (hidden) {
            case "PAST_DATE" -> s.setSessionDate(TODAY.minusDays(1));
            case "PAST_TIME" -> { s.setSessionDate(TODAY); s.setStartTime(NOW.minusSeconds(1)); }
            case "EXACT_START" -> { s.setSessionDate(TODAY); s.setStartTime(NOW); }
            case "INACTIVE_CLASS" -> jdbc.update("UPDATE sport_class SET is_active = 0 WHERE id = ?", sportClass.getId());
            case "INACTIVE_FACILITY" -> jdbc.update("UPDATE facility SET is_active = 0 WHERE id = ?", facility.getId());
            case "INACTIVE_COACH" -> jdbc.update("UPDATE user_account SET status = 'INACTIVE' WHERE id = ?", coach.getId());
            case "UNQUALIFIED_COACH" -> jdbc.update("DELETE FROM coach_sport WHERE coach_id = ?", coach.getId());
            case "NO_COACH" -> s.setCoach(null);
            case "OVERNIGHT" -> { s.setStartTime(LocalTime.of(23, 0)); s.setEndTime(LocalTime.of(1, 0)); }
            default -> s.setStatus(SessionStatus.valueOf(hidden));
        }
        save(s);
        assertThat(ids(query())).isEmpty();
        assertThatThrownBy(() -> service.getSession(s.getId(), member)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getSession(s.getId(), manager)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void sameDayFutureSelfTrainingWithNullCoachRemainsVisibleAndEligible() {
        var s = session(); s.setSessionDate(TODAY); s.setStartTime(NOW.plusSeconds(1));
        s.setTrainingType(TrainingFormat.SELF_TRAINING); s.setCoach(null); s.setSportClass(null); save(s);
        var registration = registration(member, sport, TrainingFormat.SELF_TRAINING, "ACTIVE", TODAY, TODAY, 1);
        assertThat(ids(query())).containsExactly(s.getId());
        assertThat(ids(new SessionSearchQuery(sport.getId(), null, null, null, null, coach.getId(), 0, 20))).isEmpty();
        var result = service.getSession(s.getId(), member);
        assertThat(result.session().coachId()).isNull();
        assertThat(result.session().coachName()).isNull();
        assertThat(result.session().facilityId()).isEqualTo(facility.getId());
        assertThat(result.eligible()).isTrue();
        assertThat(result.suitableRegistrations()).extracting(SessionDetailResponse.SuitableRegistration::registrationId).containsExactly(registration.getId());
    }

    @ParameterizedTest @ValueSource(strings = {"ELIGIBLE", "MISSING", "ZERO", "EXPIRED_DATE", "EXPIRED_STATUS", "FUTURE_START",
            "WRONG_SPORT", "WRONG_FORMAT", "PENDING_PAYMENT", "CANCELLED", "REFUNDED", "OTHER_MEMBER"})
    void eligibilityUsesOwnedMatchingPurchasedRegistrationsAgainstRealSql(String scenario) {
        var s = session();
        if (!scenario.equals("MISSING")) registration(scenario.equals("OTHER_MEMBER") ? account("MEMBER") : member,
                scenario.equals("WRONG_SPORT") ? newSport() : sport,
                scenario.equals("WRONG_FORMAT") ? TrainingFormat.SELF_TRAINING : TrainingFormat.COACH_LED,
                switch (scenario) { case "EXPIRED_STATUS" -> "EXPIRED"; case "PENDING_PAYMENT", "CANCELLED", "REFUNDED" -> scenario; default -> "ACTIVE"; },
                scenario.equals("FUTURE_START") ? TODAY.plusDays(2) : TODAY,
                scenario.equals("EXPIRED_DATE") ? TODAY : TODAY.plusDays(29), scenario.equals("ZERO") ? 0 : 5);
        var result = service.getSession(s.getId(), member);
        assertThat(result.eligible()).isEqualTo(scenario.equals("ELIGIBLE"));
        if (scenario.equals("ELIGIBLE")) {
            assertThat(result.suitableRegistrations()).hasSize(1);
            assertThat(result.reason()).isNull();
        } else {
            assertThat(result.suitableRegistrations()).isEmpty();
            assertThat(result.reason()).isEqualTo(switch (scenario) {
                case "ZERO" -> NO_SESSIONS_LEFT;
                case "EXPIRED_DATE", "EXPIRED_STATUS" -> PACKAGE_EXPIRED_BY_THEN;
                case "FUTURE_START" -> PACKAGE_NOT_STARTED_BY_THEN;
                default -> NO_PACKAGE;
            });
        }
    }

    @Test void actualBookingCancellationRestoresFreeSeatAndCreditsAndCancelledBookingIsNotCurrent() {
        var s = session();
        var r = registration(member, sport, TrainingFormat.COACH_LED, "ACTIVE", TODAY, TODAY.plusDays(29), 5);
        var request = new BookingCreateRequest(); request.setSessionId(s.getId()); request.setPackageRegistrationId(r.getId());
        var booking = bookingService.createBooking(request, member);
        assertThat(service.getSession(s.getId(), member).reason()).isEqualTo(ALREADY_BOOKED);
        assertThat(service.getSession(s.getId(), manager).session().freeSeats()).isEqualTo(9);
        bookingService.cancelBooking(booking.getId(), member);
        var result = service.getSession(s.getId(), member);
        assertThat(result.session().bookedCount()).isZero();
        assertThat(result.session().freeSeats()).isEqualTo(10);
        assertThat(result.alreadyBooked()).isFalse();
        assertThat(result.eligible()).isTrue();
        assertThat(result.suitableRegistrations().getFirst().remainingSessions()).isEqualTo(5);
    }

    @Test void multipleRegistrationsHaveDeterministicUsableOrderAndInclusiveSessionEndDate() {
        var s = session();
        registration(member, sport, TrainingFormat.COACH_LED, "ACTIVE", TODAY, TODAY, 5);
        registration(member, sport, TrainingFormat.COACH_LED, "ACTIVE", TODAY, TODAY.plusDays(20), 0);
        var later = registration(member, sport, TrainingFormat.COACH_LED, "ACTIVE", TODAY, TODAY.plusDays(10), 3);
        var first = registration(member, sport, TrainingFormat.COACH_LED, "ACTIVE", s.getSessionDate(), s.getSessionDate(), 2);
        var second = registration(member, sport, TrainingFormat.COACH_LED, "ACTIVE", s.getSessionDate(), s.getSessionDate(), 4);
        var result = service.getSession(s.getId(), member);
        assertThat(result.eligible()).isTrue();
        assertThat(result.suitableRegistrations()).extracting(SessionDetailResponse.SuitableRegistration::registrationId)
                .containsExactly(first.getId(), second.getId(), later.getId());
    }

    @Test void extremeSqlDateBoundsAreSafeAndInvalidPaginationIs400AtHttp() throws Exception {
        var s = session();
        assertThat(ids(new SessionSearchQuery(sport.getId(), LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31),
                null, null, null, 0, 100))).containsExactly(s.getId());
        for (String[] parameter : List.of(new String[]{"page", "-1"}, new String[]{"size", "0"},
                new String[]{"size", "101"}, new String[]{"from", "10000-01-01"})) {
            mvc.perform(get("/api/v1/sessions").param(parameter[0], parameter[1]).with(user(new CustomUserDetails(member))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test void managerCancellationHidesSessionWithoutChangingReadPolicyOrBookingRefundRules() {
        var s = session();
        managerSessions.cancelSession(s.getId(), new SessionCancelRequest("Fixture cancellation"), manager);
        assertThat(ids(query())).isEmpty();
        assertThatThrownBy(() -> service.getSession(s.getId(), member)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void staleCachedCapacityDoesNotPromiseEligibilityWhenExistingWriteWouldReject() {
        var s = session(); s.setBookedCount(s.getCapacity()); save(s);
        var r = registration(member, sport, TrainingFormat.COACH_LED, "ACTIVE", TODAY, TODAY.plusDays(29), 5);
        var result = service.getSession(s.getId(), member);
        assertThat(result.session().bookedCount()).isZero();
        assertThat(result.session().freeSeats()).isEqualTo(10);
        assertThat(result.session().full()).isFalse();
        assertThat(result.eligible()).isFalse();
        assertThat(result.reason()).isEqualTo(CAPACITY_UNAVAILABLE);
        var request = new BookingCreateRequest(); request.setSessionId(s.getId()); request.setPackageRegistrationId(r.getId());
        assertThatThrownBy(() -> bookingService.createBooking(request, member)).isInstanceOf(BusinessRuleException.class)
                .hasMessage("Session is fully booked");
        assertThat(jdbc.queryForObject("SELECT remaining_sessions FROM sport_package_registration WHERE id = ?", Integer.class, r.getId()))
                .isEqualTo(5);
    }

    @Test void alreadyBookedPrecedesFullAndUnownedPackagesNeverLeakAcrossMembers() {
        var s = session(); s.setCapacity(1); save(s);
        booking(s, member, BookingStatus.CONFIRMED);
        var other = account("MEMBER");
        registration(member, sport, TrainingFormat.COACH_LED, "ACTIVE", TODAY, TODAY.plusDays(29), 5);
        var owner = service.getSession(s.getId(), member);
        assertThat(owner.reason()).isEqualTo(ALREADY_BOOKED);
        assertThat(owner.bookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        var stranger = service.getSession(s.getId(), other);
        assertThat(stranger.alreadyBooked()).isFalse();
        assertThat(stranger.reason()).isEqualTo(FULL);
        assertThat(stranger.suitableRegistrations()).isEmpty();
    }

    @Test void coachProfileJoinsDoNotDuplicateSessionsAndBoundUpcomingAtTwentyWithNoSecrets() throws Exception {
        var otherSport = newSport();
        jdbc.update("INSERT INTO coach_sport(coach_id, sport_id) VALUES (?, ?)", coach.getId(), otherSport.getId());
        for (int i = 0; i < 2; i++) jdbc.update("INSERT INTO coach_certification(coach_id, sport_id, name, issuer, is_verified, document_url) VALUES (?, ?, ?, ?, 1, ?)",
                coach.getId(), sport.getId(), code(), "Public issuer", "private-document-url");
        for (int i = 0; i < 25; i++) session();
        var draft = session(); draft.setStatus(SessionStatus.DRAFT); save(draft);
        var cancelled = session(); cancelled.setStatus(SessionStatus.CANCELLED); save(cancelled);
        var past = session(); past.setSessionDate(TODAY.minusDays(1)); save(past);
        var profile = profiles.getProfile(coach.getId(), member);
        assertThat(profile.sports()).hasSize(2);
        assertThat(profile.certifications()).hasSize(2);
        assertThat(profile.bio()).isEqualTo("Public biography");
        assertThat(profile.upcomingSessions().content()).hasSize(20);
        assertThat(profile.upcomingSessions().totalElements()).isEqualTo(25);
        assertThat(profile.upcomingSessions().content()).extracting(SessionResponse::id).doesNotHaveDuplicates();
        String json = mapper.writeValueAsString(profile);
        assertThat(json).doesNotContain("@example.test", "PrivateFixtureHash", "passwordHash", "userAccount", "documentUrl", "createdAt", "private-document-url");
        jdbc.update("UPDATE coach_profile SET is_public = 0 WHERE user_id = ?", coach.getId());
        assertThatThrownBy(() -> profiles.getProfile(coach.getId(), member)).isInstanceOf(ResourceNotFoundException.class);
    }

    static Stream<Arguments> roleMatrix() {
        return Stream.of("MEMBER", "RECEPTIONIST", "MANAGER", "COACH", "ANONYMOUS").flatMap(role ->
                Stream.of("LIST", "DETAIL", "PROFILE").map(endpoint -> Arguments.of(role, endpoint)));
    }

    @ParameterizedTest @MethodSource("roleMatrix")
    void httpRoleMatrixAndMemberInjectionAreEnforced(String role, String endpoint) throws Exception {
        var s = session();
        registration(member, sport, TrainingFormat.COACH_LED, "ACTIVE", TODAY, TODAY.plusDays(29), 5);
        var actor = switch (role) { case "MEMBER" -> member; case "MANAGER" -> manager; case "COACH" -> coach.getUserAccount();
            case "RECEPTIONIST" -> account("RECEPTIONIST"); default -> null; };
        String path = switch (endpoint) { case "LIST" -> "/api/v1/sessions"; case "DETAIL" -> "/api/v1/sessions/" + s.getId();
            default -> "/api/v1/coaches/" + coach.getId(); };
        var request = get(path).param("sportId", sport.getId().toString()).param("memberId", member.getId().toString());
        var response = mvc.perform(actor == null ? request.with(anonymous()) : request.with(user(new CustomUserDetails(actor))))
                .andExpect(status().is(role.equals("ANONYMOUS") ? 401 : role.equals("COACH") ? 403 : 200)).andReturn();
        if (endpoint.equals("DETAIL") && actor != null && !role.equals("COACH")) {
            JsonNode json = mapper.readTree(response.getResponse().getContentAsString());
            assertThat(json.get("memberContext").asBoolean()).isEqualTo(role.equals("MEMBER"));
            if (!role.equals("MEMBER")) {
                assertThat(json.get("eligible").isNull()).isTrue();
                assertThat(json.get("alreadyBooked").isNull()).isTrue();
                assertThat(json.get("suitableRegistrations").size()).isZero();
            }
        }
    }
}
