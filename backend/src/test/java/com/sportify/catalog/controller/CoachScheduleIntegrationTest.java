package com.sportify.catalog.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.dto.*;
import com.sportify.catalog.entity.*;
import com.sportify.catalog.repository.*;
import com.sportify.catalog.service.*;
import com.sportify.identity.entity.*;
import com.sportify.identity.repository.*;
import com.sportify.identity.security.*;
import jakarta.persistence.EntityManager;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Actual SQL Server values, ownership, privacy, bounded query counts and BE-10/12 interoperability. CI only. */
@ActiveProfiles(value = {"dev", "test"}, inheritProfiles = false)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@TestPropertySource(properties = {"spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.sportify.catalog.controller.CoachScheduleIntegrationTest$SqlCapture"})
@Transactional
public class CoachScheduleIntegrationTest extends AbstractIntegrationTest {
    static final String URL = "/api/v1/coach/sessions";
    static final SecureRandom RANDOM = new SecureRandom();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired CoachProfileRepository coaches;
    @Autowired CoachSportRepository coachSports;
    @Autowired MemberProfileRepository members;
    @Autowired SportRepository sports;
    @Autowired FacilityRepository facilities;
    @Autowired SportClassRepository classes;
    @Autowired SportPackageRepository packages;
    @Autowired SportPackageRegistrationRepository registrations;
    @Autowired ClassSessionRepository sessions;
    @Autowired BookingRepository bookings;
    @Autowired CoachScheduleReadRepository reads;
    @Autowired CoachScheduleService teaching;
    @Autowired ManagerSessionService scheduling;
    @Autowired BookingService bookingService;
    @Autowired JwtService jwt;
    @Autowired Clock clock;
    final String marker = "be14" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    int serial;
    UserAccount manager, coachA, coachB, memberA, memberB, memberC, memberD;
    CoachProfile profileA, profileB;
    Sport sportA, sportB;
    Facility facilityA, facilityB;
    SportClass classA, classB;
    SportPackage packageA, packageB;
    SportPackageRegistration registrationA, registrationB, alternative;
    LocalDate day;
    ClassSession outsideBefore, first, owned, tied, last, outsideAfter, foreign, unassigned;
    Booking confirmedA, confirmedB, legacy, cancelled, foreignBooking;

    public static class SqlCapture implements StatementInspector {
        static final ThreadLocal<List<String>> SQL = ThreadLocal.withInitial(ArrayList::new);
        @Override public String inspect(String sql) { SQL.get().add(sql.toLowerCase(Locale.ROOT)); return sql; }
    }

    @BeforeEach void fixture() {
        day = LocalDate.now(clock).plusDays(10);
        manager = account("MANAGER"); coachA = account("COACH"); coachB = account("COACH");
        memberA = member(); memberB = member(); memberC = member(); memberD = member();
        memberC.setPhone(null); users.saveAndFlush(memberC);
        sportA = sport(); sportB = sport();
        profileA = profile(coachA); profileB = profile(coachB);
        coachSports.saveAndFlush(CoachSport.of(coachA.getId(), sportA.getId()));
        coachSports.saveAndFlush(CoachSport.of(coachA.getId(), sportB.getId()));
        coachSports.saveAndFlush(CoachSport.of(coachB.getId(), sportA.getId()));
        facilityA = facility(sportA); facilityB = facility(sportB);
        classA = sportClass(sportA); classB = sportClass(sportB);
        packageA = sportPackage("Original package"); packageB = sportPackage("Alternative package");
        registrationA = registration(memberA, packageA); registrationB = registration(memberB, packageA);
        alternative = registration(memberA, packageB);
        outsideBefore = session(profileA, day.minusDays(1), 8, SessionStatus.PUBLISHED);
        first = session(profileA, day, 9, SessionStatus.DRAFT);
        owned = session(profileA, day, 10, SessionStatus.PUBLISHED);
        tied = session(profileA, day, 10, SessionStatus.COMPLETED);
        last = session(profileA, day.plusDays(1), 11, SessionStatus.CANCELLED);
        outsideAfter = session(profileA, day.plusDays(2), 8, SessionStatus.PUBLISHED);
        foreign = session(profileB, day, 11, SessionStatus.PUBLISHED);
        unassigned = session(null, day, 12, SessionStatus.PUBLISHED);
        confirmedA = booking(owned, memberA, registrationA, BookingStatus.CONFIRMED, day.minusDays(2).atTime(9, 0));
        confirmedB = booking(owned, memberB, registrationB, BookingStatus.CONFIRMED, confirmedA.getBookedAt());
        legacy = booking(owned, memberC, null, BookingStatus.CONFIRMED, day.minusDays(2).atTime(10, 0));
        cancelled = booking(owned, memberD, null, BookingStatus.CANCELLED, confirmedA.getBookedAt());
        foreignBooking = booking(foreign, memberA, registrationA, BookingStatus.CONFIRMED, confirmedA.getBookedAt());
        jdbc.update("UPDATE class_session SET booked_count=9 WHERE id=?", owned.getId());
        em.clear(); SqlCapture.SQL.get().clear();
    }
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); SqlCapture.SQL.remove(); }
    String code() { return marker + (++serial); }
    UserAccount account(String role) {
        return users.saveAndFlush(UserAccount.builder().email(code() + "@example.test").fullName(role + " " + code())
                .phone(Long.toString(RANDOM.nextLong(100_000_000_000L, 1_000_000_000_000L)))
                .passwordHash("test-only-non-credential").address("Private address")
                .role(roles.findByCode(role).orElseThrow()).build());
    }
    UserAccount member() {
        var account = account("MEMBER");
        members.saveAndFlush(MemberProfile.builder().userAccount(account).memberCode(code()).staffNote("Private staff note").build());
        return account;
    }
    Sport sport() { return sports.saveAndFlush(Sport.builder().code(code()).name(code()).venueType(SportVenueType.INDOOR).build()); }
    CoachProfile profile(UserAccount account) {
        return coaches.saveAndFlush(CoachProfile.builder().userAccount(account).primarySportId(sportA.getId())
                .bio("Private biography").isPublic(false).build());
    }
    Facility facility(Sport sport) {
        return facilities.saveAndFlush(Facility.builder().code(code()).name(code()).sport(sport)
                .facilityType(FacilityType.COURT).capacity(40).build());
    }
    SportClass sportClass(Sport sport) {
        // Deliberately different class default: per-session ownership is authoritative.
        return classes.saveAndFlush(SportClass.builder().code(code()).name(code()).sport(sport).coach(profileB).maxMembers(40).build());
    }
    SportPackage sportPackage(String name) {
        return packages.saveAndFlush(SportPackage.builder().code(code()).name(name).sport(sportA)
                .trainingFormat(TrainingFormat.COACH_LED).durationDays(90).sessionCount(10).sessionMinutes(60)
                .priceAmount(BigDecimal.ZERO).build());
    }
    SportPackageRegistration registration(UserAccount account, SportPackage selected) {
        return registrations.saveAndFlush(SportPackageRegistration.builder().registrationCode(code())
                .member(members.findById(account.getId()).orElseThrow()).sportPackage(selected)
                .channel(RegistrationChannel.RECEPTION).originalPrice(BigDecimal.ZERO).paidAmount(BigDecimal.ZERO)
                .totalSessions(10).remainingSessions(10).startDate(LocalDate.now(clock).minusDays(1))
                .endDate(LocalDate.now(clock).plusDays(80)).status(PackageRegistrationStatus.ACTIVE).createdByUser(manager).build());
    }
    ClassSession session(CoachProfile profile, LocalDate date, int hour, SessionStatus status) {
        return sessions.saveAndFlush(ClassSession.builder().sportClass(profile == null ? null : classA).sport(sportA)
                .coach(profile).facility(facilityA).sessionDate(date).startTime(LocalTime.of(hour, 0)).endTime(LocalTime.of(hour + 1, 0))
                .trainingType(profile == null ? TrainingFormat.SELF_TRAINING : TrainingFormat.COACH_LED)
                .capacity(20).status(status).build());
    }
    Booking booking(ClassSession session, UserAccount member, SportPackageRegistration registration,
            BookingStatus status, LocalDateTime bookedAt) {
        return bookings.saveAndFlush(Booking.builder().bookingCode(code()).session(session)
                .member(members.findById(member.getId()).orElseThrow()).packageRegistration(registration).status(status)
                .bookedAt(bookedAt).createdAt(bookedAt.minusHours(2)).build());
    }
    void authenticate(UserAccount account) {
        var principal = new CustomUserDetails(account);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
    MockHttpServletRequestBuilder calendar(UserAccount reader) {
        return get(URL).with(user(new CustomUserDetails(reader))).param("from", day.toString()).param("to", day.plusDays(1).toString());
    }
    MockHttpServletRequestBuilder roster(UserAccount reader, Long id) {
        return get(URL + "/" + id + "/roster").with(user(new CustomUserDetails(reader)));
    }
    JsonNode call(MockHttpServletRequestBuilder request, int expected) throws Exception {
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(expected);
        return json.readTree(response.getContentAsByteArray());
    }
    List<Long> ids(JsonNode rows, String field) {
        List<Long> result = new ArrayList<>(); rows.forEach(row -> result.add(row.path(field).asLong())); return result;
    }
    JsonNode row(JsonNode rows, Long id) {
        for (var row : rows) if (row.path("id").asLong() == id) return row;
        throw new AssertionError("Expected session row is absent");
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void eachCoachSeesOnlyOwnSessionAssignment(boolean firstCoach) throws Exception {
        var result = call(calendar(firstCoach ? coachA : coachB), 200);
        assertThat(ids(result, "id")).containsExactlyElementsOf(firstCoach
                ? List.of(first.getId(), owned.getId(), tied.getId(), last.getId()) : List.of(foreign.getId()));
        for (var row : result) assertThat(row.path("coachId").asLong()).isEqualTo((firstCoach ? coachA : coachB).getId());
    }

    @Test void managerOmittedCoachGetsEveryAssignedSessionAndCanFilter() throws Exception {
        var result = call(calendar(manager), 200);
        var expected = jdbc.queryForList("SELECT id FROM class_session WHERE coach_id IS NOT NULL AND session_date BETWEEN ? AND ? ORDER BY session_date,start_time,id",
                Long.class, day, day.plusDays(1));
        assertThat(ids(result, "id")).containsExactlyElementsOf(expected).doesNotContain(unassigned.getId());
        assertThat(ids(call(calendar(manager).param("coachId", coachB.getId().toString()), 200), "id")).containsExactly(foreign.getId());
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void coachIdOverrideCannotCrossAccount(boolean firstCoach) throws Exception {
        var reader = firstCoach ? coachA : coachB; var other = firstCoach ? coachB : coachA;
        var result = call(calendar(reader).param("coachId", other.getId().toString()), 403);
        assertThat(result.has("memberPhone")).isFalse();
        assertThat(SqlCapture.SQL.get()).noneMatch(sql -> sql.contains("from class_session"));
        assertThat(ids(call(calendar(reader).param("coachId", reader.getId().toString()), 200), "id")).isNotEmpty();
    }

    @Test void calendarBoundariesStatusTiesAndActualCountMapCorrectly() throws Exception {
        var result = call(calendar(coachA), 200);
        assertThat(ids(result, "id")).containsExactly(first.getId(), owned.getId(), tied.getId(), last.getId())
                .doesNotContain(outsideBefore.getId(), outsideAfter.getId());
        assertThat(row(result, first.getId()).path("status").asText()).isEqualTo("DRAFT");
        assertThat(row(result, tied.getId()).path("status").asText()).isEqualTo("COMPLETED");
        assertThat(row(result, last.getId()).path("status").asText()).isEqualTo("CANCELLED");
        var item = row(result, owned.getId());
        assertThat(item.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(item.path("bookedCount").asInt()).isEqualTo(3);
        assertThat(item.path("capacity").asInt()).isEqualTo(20);
        assertThat(item.path("classId").asLong()).isEqualTo(classA.getId());
        assertThat(item.path("sportId").asLong()).isEqualTo(sportA.getId());
        assertThat(item.path("facilityId").asLong()).isEqualTo(facilityA.getId());
        assertThat(item.path("sessionDate").asText()).isEqualTo(day.toString());
        assertThat(item.path("startTime").asText()).isEqualTo("10:00:00");
        assertThat(item.path("endTime").asText()).isEqualTo("11:00:00");
        assertThat(jdbc.queryForObject("SELECT booked_count FROM class_session WHERE id=?", Integer.class, owned.getId())).isEqualTo(9);
    }

    @Test void multiSportFacilityAndPastPrivateAssignmentsRemainVisible() throws Exception {
        var past = session(profileA, LocalDate.now(clock).minusDays(2), 14, SessionStatus.COMPLETED);
        jdbc.update("UPDATE class_session SET class_id=?,sport_id=?,facility_id=? WHERE id=?", classB.getId(), sportB.getId(), facilityB.getId(), first.getId());
        jdbc.update("UPDATE facility SET is_active=0 WHERE id=?", facilityB.getId());
        jdbc.update("UPDATE sport_class SET is_active=0 WHERE id=?", classB.getId());
        em.clear();
        var multi = row(call(calendar(coachA), 200), first.getId());
        assertThat(multi.path("sportName").asText()).isEqualTo(sportB.getName());
        assertThat(multi.path("facilityName").asText()).isEqualTo(facilityB.getName());
        assertThat(ids(call(get(URL).with(user(new CustomUserDetails(coachA)))
                .param("from", past.getSessionDate().toString()).param("to", past.getSessionDate().toString()), 200), "id")).containsExactly(past.getId());
    }

    @Test void nullableClassFacilityAndEmptyAssignedCoachCalendar() throws Exception {
        jdbc.update("UPDATE class_session SET class_id=NULL,facility_id=NULL WHERE id=?", first.getId());
        em.clear(); var result = row(call(calendar(coachA), 200), first.getId());
        assertThat(result.path("classId").isNull()).isTrue(); assertThat(result.path("facilityName").isNull()).isTrue();
        var empty = account("COACH"); profile(empty); em.clear();
        assertThat(call(calendar(empty), 200)).isEmpty();
    }

    @Test void maximumSqlDateAnd62DayCalendarAreSafe() throws Exception {
        var maximum = session(profileA, LocalDate.of(9999, 12, 31), 9, SessionStatus.DRAFT); em.clear();
        assertThat(ids(call(get(URL).with(user(new CustomUserDetails(coachA)))
                .param("from", "9999-12-31").param("to", "9999-12-31"), 200), "id")).containsExactly(maximum.getId());
        var range = call(get(URL).with(user(new CustomUserDetails(coachA)))
                .param("from", day.toString()).param("to", day.plusDays(61).toString()), 200);
        assertThat(ids(range, "id")).contains(outsideAfter.getId());
    }

    @ParameterizedTest @CsvSource({"from,bad", "from,0000-01-01", "to,2026-02-30", "coachId,0", "coachId,-1", "coachId,bad"})
    void malformedScheduleParameters400(String field, String value) throws Exception {
        var req = get(URL).with(user(new CustomUserDetails(manager)))
                .param("from", field.equals("from") ? value : day.toString())
                .param("to", field.equals("to") ? value : day.plusDays(1).toString());
        if (field.equals("coachId")) req.param(field, value);
        assertThat(call(req, 400).path("status").asInt()).isEqualTo(400);
    }

    @ParameterizedTest @ValueSource(strings = {"from", "to", "both"})
    void missingDateBounds400(String missing) throws Exception {
        var req = get(URL).with(user(new CustomUserDetails(manager)));
        if (!missing.equals("from") && !missing.equals("both")) req.param("from", day.toString());
        if (!missing.equals("to") && !missing.equals("both")) req.param("to", day.toString());
        call(req, 400);
    }

    @Test void reversedAndOversizedRanges400() throws Exception {
        call(get(URL).with(user(new CustomUserDetails(manager))).param("from", day.plusDays(1).toString()).param("to", day.toString()), 400);
        call(get(URL).with(user(new CustomUserDetails(manager))).param("from", day.toString()).param("to", day.plusDays(62).toString()), 400);
    }

    @Test void unknownCoach404AndMissingProfile403NeverReadRoster() throws Exception {
        call(calendar(manager).param("coachId", Long.toString(Long.MAX_VALUE)), 404);
        var missing = account("COACH"); em.clear();
        call(calendar(missing), 403); call(roster(missing, owned.getId()), 403);
        assertThat(SqlCapture.SQL.get()).noneMatch(sql -> sql.contains("from booking"));
    }

    @ParameterizedTest @ValueSource(strings = {"MEMBER", "RECEPTIONIST"})
    void forbiddenRolesCannotReadEitherEndpoint(String role) throws Exception {
        var reader = role.equals("MEMBER") ? memberA : account(role); em.clear(); SqlCapture.SQL.get().clear();
        var calendarError = call(calendar(reader), 403); var rosterError = call(roster(reader, owned.getId()), 403);
        assertThat(calendarError.toString()).doesNotContain(memberA.getPhone(), memberA.getFullName());
        assertThat(rosterError.toString()).doesNotContain(memberA.getPhone(), memberA.getFullName(), "Private staff note");
        assertThat(SqlCapture.SQL.get()).noneMatch(sql -> sql.contains("from booking") || sql.contains("from class_session"));
    }

    @ParameterizedTest @ValueSource(strings = {"", "/1/roster"})
    void anonymous401(String suffix) throws Exception {
        call(get(URL + suffix).param("from", day.toString()).param("to", day.toString()), 401);
        assertThat(SqlCapture.SQL.get()).isEmpty();
    }

    @Test void realJwtWorksAndCurrentInactiveAccountIsRejected() throws Exception {
        String token = jwt.generateToken(new CustomUserDetails(coachA));
        call(get(URL).header("Authorization", "Bearer " + token).param("from", day.toString()).param("to", day.toString()), 200);
        call(get(URL + "/" + owned.getId() + "/roster").header("Authorization", "Bearer " + token), 200);
        jdbc.update("UPDATE user_account SET status='INACTIVE' WHERE id=?", coachA.getId()); em.clear();
        call(get(URL).header("Authorization", "Bearer " + token).param("from", day.toString()).param("to", day.toString()), 401);
        call(get(URL + "/" + owned.getId() + "/roster").header("Authorization", "Bearer " + token), 401);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void foreignRoster403BeforeAnyMemberQuery(boolean firstCoach) throws Exception {
        var reader = firstCoach ? coachA : coachB; var target = firstCoach ? foreign : owned;
        var result = call(roster(reader, target.getId()).param("coachId", reader.getId().toString()).header("X-Coach-Id", "1"), 403);
        assertThat(result.toString()).doesNotContain(memberA.getPhone(), memberA.getFullName());
        assertThat(SqlCapture.SQL.get()).noneMatch(sql -> sql.contains("from booking"));
    }

    @Test void coachlessSelfTrainingDeniedToCoachAndAllowedToManager() throws Exception {
        booking(unassigned, memberA, null, BookingStatus.CONFIRMED, day.minusDays(1).atTime(9, 0)); em.clear();
        call(roster(coachA, unassigned.getId()), 403);
        var result = call(roster(manager, unassigned.getId()), 200);
        assertThat(result).hasSize(1); assertThat(result.get(0).path("memberName").asText()).isEqualTo(memberA.getFullName());
    }

    @Test void managerCanReadInactiveCoachHistoryAndCurrentMemberContacts() throws Exception {
        jdbc.update("UPDATE user_account SET status='INACTIVE' WHERE id=?", coachB.getId());
        jdbc.update("UPDATE user_account SET full_name=? WHERE id=?", "Nguy\u1ec5n", memberA.getId());
        em.clear();
        assertThat(ids(call(calendar(manager).param("coachId", coachB.getId().toString()), 200), "id"))
                .containsExactly(foreign.getId());
        var result = call(roster(manager, foreign.getId()), 200);
        assertThat(result.get(0).path("memberName").asText()).isEqualTo("Nguy\u1ec5n");
        assertThat(result.get(0).path("memberPhone").asText()).isEqualTo(memberA.getPhone());
    }

    @Test void confirmedRosterMapsSelectedRegistrationContactAndBookingTimeWithStableTies() throws Exception {
        var result = call(roster(coachA, owned.getId()), 200);
        assertThat(ids(result, "bookingId")).containsExactly(confirmedA.getId(), confirmedB.getId(), legacy.getId())
                .doesNotContain(cancelled.getId(), foreignBooking.getId());
        var first = result.get(0);
        assertThat(first.path("memberCode").asText()).isEqualTo(jdbc.queryForObject("SELECT member_code FROM member_profile WHERE user_id=?", String.class, memberA.getId()));
        assertThat(first.path("memberName").asText()).isEqualTo(memberA.getFullName());
        assertThat(first.path("memberPhone").asText()).isEqualTo(memberA.getPhone());
        assertThat(first.path("packageRegistrationId").asLong()).isEqualTo(registrationA.getId()).isNotEqualTo(alternative.getId());
        assertThat(first.path("packageId").asLong()).isEqualTo(packageA.getId());
        assertThat(first.path("packageName").asText()).isEqualTo("Original package");
        assertThat(first.path("bookedAt").asText()).isEqualTo(confirmedA.getBookedAt().toString() + ":00");
        assertThat(first.path("bookedAt").asText()).isNotEqualTo(confirmedA.getCreatedAt().toString());
        var nulls = result.get(2);
        assertThat(nulls.path("memberPhone").isNull()).isTrue();
        assertThat(nulls.path("packageRegistrationId").isNull()).isTrue();
        assertThat(nulls.path("packageId").isNull()).isTrue();
        assertThat(nulls.path("packageName").isNull()).isTrue();
    }

    @ParameterizedTest @ValueSource(strings = {"EXPIRED", "REFUNDED"})
    void historicalRegistrationIsNeverReplacedByActiveAlternative(String status) throws Exception {
        jdbc.update("UPDATE sport_package_registration SET status=? WHERE id=?", status, registrationA.getId()); em.clear();
        var result = call(roster(manager, owned.getId()), 200);
        assertThat(result.get(0).path("packageRegistrationId").asLong()).isEqualTo(registrationA.getId());
        assertThat(result.get(0).path("packageName").asText()).isEqualTo("Original package");
        assertThat(result).hasSize(3);
    }

    @Test void inconsistentForeignRegistrationDoesNotExposeOtherMembersPackage() throws Exception {
        jdbc.update("UPDATE booking SET package_registration_id=? WHERE id=?", registrationB.getId(), confirmedA.getId()); em.clear();
        var result = call(roster(coachA, owned.getId()), 200);
        var first = result.get(0);
        assertThat(first.path("bookingId").asLong()).isEqualTo(confirmedA.getId());
        assertThat(first.path("memberName").asText()).isEqualTo(memberA.getFullName());
        assertThat(first.path("packageRegistrationId").isNull()).isTrue();
        assertThat(first.path("packageName").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT package_registration_id FROM booking WHERE id=?", Long.class, confirmedA.getId())).isEqualTo(registrationB.getId());
    }

    @Test void emptyRoster200AndUnknownSession404() throws Exception {
        assertThat(call(roster(coachA, first.getId()), 200)).isEmpty();
        assertThat(call(roster(manager, last.getId()), 200)).isEmpty();
        assertThat(call(roster(coachA, Long.MAX_VALUE), 404).path("message").asText()).isEqualTo("Session not found");
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "bad"})
    void invalidRosterId400(String id) throws Exception {
        call(get(URL + "/" + id + "/roster").with(user(new CustomUserDetails(coachA))), 400);
    }

    @Test void rosterProjectionHasOnlyEightAllowedFieldsAndOnePiiQuery() throws Exception {
        var result = call(roster(coachA, owned.getId()), 200);
        for (var row : result) {
            Set<String> fields = new HashSet<>(); row.fieldNames().forEachRemaining(fields::add);
            assertThat(fields).containsExactlyInAnyOrder("bookingId", "memberCode", "memberName", "memberPhone",
                    "packageRegistrationId", "packageId", "packageName", "bookedAt");
        }
        assertThat(result.toString()).doesNotContain("Private staff note", "Private address", "Private biography", "test-only-non-credential", "@example.test");
        var dataSql = SqlCapture.SQL.get().stream().filter(sql -> sql.contains("from booking")).toList();
        assertThat(dataSql).hasSize(1);
        assertThat(dataSql.getFirst()).doesNotContain("password_hash", "staff_note", "email", "address", "payment_id", "cancel_reason");
    }

    @Test void calendarReturnsMoreThanDefaultPageWithoutNPlusOneOrSilentTruncation() throws Exception {
        for (int index = 0; index < 35; index++) session(profileA, day, 14, SessionStatus.COMPLETED);
        em.clear(); SqlCapture.SQL.get().clear();
        var result = call(calendar(coachA), 200);
        assertThat(result).hasSize(39);
        var querySql = SqlCapture.SQL.get();
        assertThat(querySql.stream().filter(sql -> sql.contains("from class_session"))).hasSize(1);
        assertThat(querySql.stream().filter(sql -> sql.contains("from booking"))).hasSize(1);
        assertThat(querySql.size()).isLessThanOrEqualTo(5);
        for (var row : result) assertThat(row.has("memberPhone")).isFalse();
    }

    @Test void rosterQueryRechecksOwnershipAfterReassignment() {
        var checked = reads.findOwnership(owned.getId()).orElseThrow();
        assertThat(checked.getCoachId()).isEqualTo(coachA.getId());
        jdbc.update("UPDATE class_session SET coach_id=? WHERE id=?", coachB.getId(), owned.getId()); em.clear();
        assertThat(reads.findRoster(owned.getId(), BookingStatus.CONFIRMED, false, coachA.getId())).isEmpty();
        assertThat(reads.findRoster(owned.getId(), BookingStatus.CONFIRMED, false, coachB.getId())).hasSize(3);
    }

    Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessions", jdbc.queryForList("SELECT * FROM class_session WHERE sport_id IN (?,?) ORDER BY id", sportA.getId(), sportB.getId()));
        result.put("bookings", jdbc.queryForList("SELECT b.* FROM booking b JOIN user_account u ON u.id=b.member_id WHERE u.email LIKE ? ORDER BY b.id", marker + "%"));
        result.put("registrations", jdbc.queryForList("SELECT * FROM sport_package_registration WHERE registration_code LIKE ? ORDER BY id", marker + "%"));
        result.put("members", jdbc.queryForList("SELECT m.* FROM member_profile m JOIN user_account u ON u.id=m.user_id WHERE u.email LIKE ? ORDER BY m.user_id", marker + "%"));
        result.put("coaches", jdbc.queryForList("SELECT c.* FROM coach_profile c JOIN user_account u ON u.id=c.user_id WHERE u.email LIKE ? ORDER BY c.user_id", marker + "%"));
        result.put("audits", jdbc.queryForObject("SELECT COUNT_BIG(*) FROM activity_log", Long.class));
        result.put("payments", jdbc.queryForObject("SELECT COUNT_BIG(*) FROM payment", Long.class));
        return result;
    }

    @Test void repeatedReadsDoNotMutateSchedulingBookingsCreditsProfilesAuditsOrPayments() throws Exception {
        var before = snapshot();
        call(calendar(coachA), 200); call(calendar(manager), 200);
        call(roster(coachA, owned.getId()), 200); call(roster(manager, foreign.getId()), 200);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void existingSchedulingBookingAndCancellationRemainCompatible() throws Exception {
        LocalDate future = day.plusDays(3);
        authenticate(manager);
        var created = scheduling.createSessions(classA.getId(), new SessionCreateRequest(future, null, null, null,
                LocalTime.of(15, 0), LocalTime.of(16, 0), facilityA.getId(), coachA.getId(), 3,
                TrainingFormat.COACH_LED, SessionStatus.PUBLISHED), manager).getFirst();
        authenticate(memberA);
        var booked = bookingService.createBooking(BookingCreateRequest.builder().sessionId(created.id())
                .packageRegistrationId(registrationA.getId()).build(), memberA);
        em.flush(); em.clear();
        var roster = call(roster(coachA, created.id()), 200);
        assertThat(ids(roster, "bookingId")).containsExactly(booked.getId());
        assertThat(roster.get(0).path("packageRegistrationId").asLong()).isEqualTo(registrationA.getId());
        assertThat(jdbc.queryForObject("SELECT remaining_sessions FROM sport_package_registration WHERE id=?", Integer.class, registrationA.getId())).isEqualTo(9);
        authenticate(memberA); bookingService.cancelBooking(booked.getId(), memberA); em.flush(); em.clear();
        assertThat(call(roster(coachA, created.id()), 200)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT remaining_sessions FROM sport_package_registration WHERE id=?", Integer.class, registrationA.getId())).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT booked_count FROM class_session WHERE id=?", Integer.class, created.id())).isZero();
        authenticate(manager); scheduling.cancelSession(created.id(), new SessionCancelRequest("Fixture cancellation"), manager); em.flush(); em.clear();
        var result = call(get(URL).with(user(new CustomUserDetails(coachA))).param("from", future.toString()).param("to", future.toString()), 200);
        assertThat(row(result, created.id()).path("status").asText()).isEqualTo("CANCELLED");
        assertThat(row(result, created.id()).path("bookedCount").asInt()).isZero();
    }

    @Test void teacherDraftVisibilityDoesNotWeakenExistingMemberDiscovery() throws Exception {
        assertThat(ids(call(calendar(coachA), 200), "id")).contains(first.getId());
        var discovery = call(get("/api/v1/sessions").with(user(new CustomUserDetails(memberA)))
                .param("coachId", coachA.getId().toString()).param("from", day.toString()).param("to", day.plusDays(1).toString()), 200);
        assertThat(ids(discovery.path("content"), "id")).contains(owned.getId()).doesNotContain(first.getId(), tied.getId(), last.getId());
    }
}
