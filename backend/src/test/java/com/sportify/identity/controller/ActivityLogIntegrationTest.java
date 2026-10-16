package com.sportify.identity.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.AbstractIntegrationTest;
import com.sportify.core.audit.*;
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
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Real SQL Server query, HTTP, authorization, privacy and writer regression checks. CI only. */
@ActiveProfiles(value = {"dev", "test"}, inheritProfiles = false)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@TestPropertySource(properties = {
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.sportify.identity.controller.ActivityLogIntegrationTest$SqlCapture"})
@Transactional
public class ActivityLogIntegrationTest extends AbstractIntegrationTest {
    private static final String URL = "/api/v1/manager/activity-logs";
    private static final String DAY = "2026-10-16";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired AuditService audit;
    @Autowired JwtService jwt;
    @Autowired EntityManager em;
    final String marker = "BE04_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    UserAccount manager;
    UserAccount member;
    UserAccount coach;
    final List<Long> ids = new ArrayList<>();
    int serial;

    public static class SqlCapture implements StatementInspector {
        static final ThreadLocal<List<String>> SQL = ThreadLocal.withInitial(ArrayList::new);
        @Override public String inspect(String sql) { SQL.get().add(sql.toLowerCase(Locale.ROOT)); return sql; }
    }

    @BeforeEach void fixtures() {
        manager = account("MANAGER", "BE04 Manager");
        member = account("MEMBER", "BE04 Alpha");
        coach = account("COACH", "BE04 Beta");
        ids.add(event(member, "PASSWORD_CHANGED", "2026-10-15T23:59:59", 901L));
        ids.add(event(member, "PASSWORD_CHANGED", DAY + "T00:00:00", 902L));
        ids.add(event(member, "LOGIN", DAY + "T12:00:00", 903L));
        ids.add(event(coach, "PASSWORD_CHANGED", DAY + "T12:00:00", 904L));
        ids.add(event(null, "PASSWORD_CHANGED", DAY + "T23:59:59", null));
        ids.add(event(member, "PASSWORD_CHANGED", "2026-10-17T00:00:00", 906L));
        em.clear();
        SqlCapture.SQL.get().clear();
    }

    @AfterEach void clearCapture() { SqlCapture.SQL.remove(); }

    UserAccount account(String role, String name) {
        return users.saveAndFlush(UserAccount.builder().email(marker + (++serial) + "@example.test")
                .fullName(name).passwordHash("test-only-non-credential").role(roles.findByCode(role).orElseThrow()).build());
    }

    long event(UserAccount actor, String action, String time, Long target) {
        return jdbc.queryForObject("""
                INSERT INTO activity_log(actor_user_id,action,entity_type,entity_id,summary,details,ip_address,created_at)
                OUTPUT INSERTED.id VALUES(?,?,?,?,?,?,?,?)
                """, Long.class, actor == null ? null : actor.getId(), action, marker, target,
                "Sensitive summary marker", "{\"note\":\"sensitive-json-marker\"}", "192.0.2.4", LocalDateTime.parse(time));
    }

    MockHttpServletRequestBuilder request() {
        return get(URL).with(user(new CustomUserDetails(manager))).param("search", marker);
    }

    JsonNode call(MockHttpServletRequestBuilder request, int expected) throws Exception {
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(expected);
        return json.readTree(response.getContentAsByteArray());
    }

    List<Long> resultIds(JsonNode response) {
        List<Long> result = new ArrayList<>();
        response.path("content").forEach(row -> result.add(row.path("id").asLong()));
        return result;
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> filters() {
        return Stream.of(
                Arguments.of("none", List.of(5,4,3,2,1,0)),
                Arguments.of("actor", List.of(5,2,1,0)),
                Arguments.of("action", List.of(5,4,3,1,0)),
                Arguments.of("from", List.of(5,4,3,2,1)),
                Arguments.of("to", List.of(4,3,2,1,0)),
                Arguments.of("range", List.of(4,3,2,1)),
                Arguments.of("actorAction", List.of(5,1,0)),
                Arguments.of("actorRange", List.of(2,1)),
                Arguments.of("actionRange", List.of(4,3,1)),
                Arguments.of("actionSearch", List.of(5,4,3,1,0)),
                Arguments.of("all", List.of(1)));
    }

    @ParameterizedTest @MethodSource("filters")
    void databaseFiltersAndCounts(String scenario, List<Integer> indexes) throws Exception {
        var req = request();
        if (Set.of("actor", "actorAction", "actorRange", "all").contains(scenario)) req.param("actorId", member.getId().toString());
        if (Set.of("action", "actorAction", "actionRange", "actionSearch", "all").contains(scenario)) req.param("action", "PASSWORD_CHANGED");
        if (Set.of("from", "range", "actorRange", "actionRange", "all").contains(scenario)) req.param("from", DAY);
        if (Set.of("to", "range", "actorRange", "actionRange", "all").contains(scenario)) req.param("to", DAY);
        var result = call(req, 200);
        assertThat(resultIds(result)).containsExactlyElementsOf(indexes.stream().map(ids::get).toList());
        assertThat(result.path("totalElements").asInt()).isEqualTo(indexes.size());
        assertThat(result.path("totalPages").asInt()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"actor", "action", "from", "to", "actorAction", "actorRange", "actionRange"})
    void standaloneFiltersMatchIndependentSqlWithoutSearch(String scenario) throws Exception {
        var req = get(URL).with(user(new CustomUserDetails(manager))).param("size", "100");
        List<String> conditions = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        if (Set.of("actor", "actorAction", "actorRange").contains(scenario)) {
            req.param("actorId", member.getId().toString());
            conditions.add("actor_user_id=?"); values.add(member.getId());
        }
        if (Set.of("action", "actorAction", "actionRange").contains(scenario)) {
            req.param("action", "PASSWORD_CHANGED");
            conditions.add("action=?"); values.add("PASSWORD_CHANGED");
        }
        if (Set.of("from", "actorRange", "actionRange").contains(scenario)) {
            req.param("from", DAY);
            conditions.add("created_at>=?"); values.add(LocalDateTime.parse(DAY + "T00:00:00"));
        }
        if (Set.of("to", "actorRange", "actionRange").contains(scenario)) {
            req.param("to", DAY);
            conditions.add("created_at<?"); values.add(LocalDateTime.parse("2026-10-17T00:00:00"));
        }
        String where = " WHERE " + String.join(" AND ", conditions);
        var expected = jdbc.queryForList("SELECT TOP(100) id FROM activity_log" + where
                + " ORDER BY created_at DESC,id DESC", Long.class, values.toArray());
        Long count = jdbc.queryForObject("SELECT COUNT_BIG(*) FROM activity_log" + where, Long.class, values.toArray());
        var result = call(req, 200);
        assertThat(resultIds(result)).containsExactlyElementsOf(expected);
        assertThat(result.path("totalElements").asLong()).isEqualTo(count);
        assertThat(result.path("totalPages").asInt()).isEqualTo((int) Math.ceil(count / 100.0));
    }

    @Test void reversedDateRejectedOnSqlServerStack() throws Exception {
        var result = call(request().param("from", "2026-10-17").param("to", DAY), 400);
        assertThat(result.path("details").has("to")).isTrue();
        assertThat(SqlCapture.SQL.get()).noneMatch(sql -> sql.contains("from activity_log"));
    }

    @Test void unfilteredEndpointIncludesRealRowsAndDefaults() throws Exception {
        var result = call(get(URL).with(user(new CustomUserDetails(manager))), 200);
        assertThat(result.path("page").asInt()).isZero();
        assertThat(result.path("size").asInt()).isEqualTo(20);
        assertThat(result.path("totalElements").asLong()).isGreaterThanOrEqualTo(6);
    }

    @ParameterizedTest @ValueSource(strings = {"MEMBER", "COACH", "RECEPTIONIST"})
    void nonManagerForbidden(String role) throws Exception {
        var actor = account(role, "BE04 Other");
        var result = call(get(URL).with(user(new CustomUserDetails(actor))), 403);
        assertThat(result.has("content")).isFalse();
        assertThat(SqlCapture.SQL.get()).noneMatch(sql -> sql.contains("from activity_log"));
    }

    @Test void anonymousRejected() throws Exception {
        assertThat(call(get(URL), 401).has("content")).isFalse();
    }

    @Test void actualAccessTokenAndInactiveAccountRejection() throws Exception {
        String token = jwt.generateToken(new CustomUserDetails(manager));
        var req = get(URL).header("Authorization", "Bearer " + token).param("search", marker);
        assertThat(resultIds(call(req, 200))).hasSize(6);
        jdbc.update("UPDATE user_account SET status='INACTIVE' WHERE id=?", manager.getId());
        em.clear();
        call(get(URL).header("Authorization", "Bearer " + token), 401);
    }

    @ParameterizedTest @CsvSource({"0,2,5:4", "1,2,3:2", "2,2,1:0", "3,2,empty"})
    void pagesStableAndCorrect(int page, int size, String expected) throws Exception {
        var result = call(request().param("page", "" + page).param("size", "" + size), 200);
        List<Long> wanted = expected.equals("empty") ? List.of() : Arrays.stream(expected.split(":"))
                .map(Integer::parseInt).map(ids::get).toList();
        assertThat(resultIds(result)).containsExactlyElementsOf(wanted);
        assertThat(result.path("totalElements").asInt()).isEqualTo(6);
        assertThat(result.path("totalPages").asInt()).isEqualTo(3);
        assertThat(result.path("page").asInt()).isEqualTo(page);
        assertThat(result.path("size").asInt()).isEqualTo(size);
    }

    @Test void filteredCountsApplyBeforePagination() throws Exception {
        var result = call(request().param("actorId", member.getId().toString()).param("size", "2"), 200);
        assertThat(resultIds(result)).containsExactly(ids.get(5), ids.get(2));
        assertThat(result.path("totalElements").asInt()).isEqualTo(4);
        assertThat(result.path("totalPages").asInt()).isEqualTo(2);
    }

    @Test void timestampBoundariesAndFinalDay() throws Exception {
        var exact = call(request().param("from", DAY + "T12:00:00").param("to", DAY + "T12:00:00"), 200);
        assertThat(resultIds(exact)).containsExactly(ids.get(3), ids.get(2));
        var finalDay = call(request().param("from", DAY).param("to", DAY), 200);
        assertThat(resultIds(finalDay)).containsExactly(ids.get(4), ids.get(3), ids.get(2), ids.get(1));
    }

    @Test void maximumSupportedDayDoesNotOverflow() throws Exception {
        long id = event(null, "LOGIN", "9999-12-31T23:59:59", null);
        assertThat(resultIds(call(request().param("from", "9999-12-31").param("to", "9999-12-31"), 200))).containsExactly(id);
    }

    @Test void unknownActorAndNoMatchesAreEmpty() throws Exception {
        var missing = call(request().param("actorId", Long.toString(Long.MAX_VALUE)), 200);
        assertThat(resultIds(missing)).isEmpty();
        assertThat(missing.path("totalElements").asInt()).isZero();
        assertThat(missing.path("totalPages").asInt()).isZero();
        assertThat(resultIds(call(request().param("action", "REFUND_COMPLETED"), 200))).isEmpty();
    }

    @Test void nullableActorsAndTargetsSurviveLeftJoins() throws Exception {
        var row = call(request(), 200).path("content").get(1);
        assertThat(row.path("id").asLong()).isEqualTo(ids.get(4));
        assertThat(row.path("actorId").isNull()).isTrue();
        assertThat(row.path("actorName").asText()).isEqualTo("System / unavailable actor");
        assertThat(row.path("actorRole").isNull()).isTrue();
        assertThat(row.path("entityId").isNull()).isTrue();
    }

    @Test void currentInactiveRenamedActorAndRoleAreReported() throws Exception {
        jdbc.update("UPDATE user_account SET full_name=?,status='INACTIVE',role_id=? WHERE id=?",
                "Nguy\u1ec5n \ud83c\udfbe", roles.findByCode("RECEPTIONIST").orElseThrow().getId(), member.getId());
        em.clear();
        var result = call(request().param("actorId", member.getId().toString()), 200);
        for (var row : result.path("content")) {
            assertThat(row.path("actorName").asText()).isEqualTo("Nguy\u1ec5n \ud83c\udfbe");
            assertThat(row.path("actorRole").asText()).isEqualTo("RECEPTIONIST");
            assertThat(row.path("entityType").asText()).isEqualTo(marker);
        }
    }

    @Test void deletedUnreferencedAccountDoesNotRemoveSystemHistory() throws Exception {
        var former = account("MEMBER", "BE04 Former");
        jdbc.update("DELETE FROM user_account WHERE id=?", former.getId());
        em.clear();
        assertThat(resultIds(call(request(), 200))).contains(ids.get(4));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.foreign_keys WHERE name='fk_al_actor' AND is_disabled=0", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT delete_referential_action FROM sys.foreign_keys WHERE name='fk_al_actor'", Integer.class)).isZero();
    }

    @Test void searchNamesActionsTypesAndExactTargetId() throws Exception {
        var name = call(get(URL).with(user(new CustomUserDetails(manager))).param("actorId", member.getId().toString()).param("search", "bE04 aLPHa"), 200);
        assertThat(resultIds(name)).containsExactly(ids.get(5), ids.get(2), ids.get(1), ids.get(0));
        var byAction = call(get(URL).with(user(new CustomUserDetails(manager))).param("actorId", member.getId().toString()).param("search", "password_changed"), 200);
        assertThat(resultIds(byAction)).containsExactly(ids.get(5), ids.get(1), ids.get(0));
        var numeric = call(get(URL).with(user(new CustomUserDetails(manager))).param("actorId", member.getId().toString()).param("search", "902"), 200);
        assertThat(resultIds(numeric)).containsExactly(ids.get(1));
    }

    @ParameterizedTest @ValueSource(strings = {"%", "_", "[", "\\", "' OR 1=1 --", "999999999999999999999999"})
    void literalSearchAndInjectionSafety(String search) throws Exception {
        // Remove wildcard characters from the other searchable fields for a meaningful negative control.
        jdbc.update("UPDATE activity_log SET action='LOGIN',entity_type=? WHERE entity_type=?", marker.replace("_", ""), marker);
        jdbc.update("UPDATE user_account SET full_name=? WHERE id=?", "literal" + search + "value", member.getId());
        em.clear();
        var result = call(get(URL).with(user(new CustomUserDetails(manager))).param("actorId", member.getId().toString()).param("search", search), 200);
        assertThat(resultIds(result)).containsExactly(ids.get(5), ids.get(2), ids.get(1), ids.get(0));
        var absent = call(get(URL).with(user(new CustomUserDetails(manager))).param("actorId", coach.getId().toString()).param("search", search), 200);
        assertThat(resultIds(absent)).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", "\t"})
    void emptySearchNoFilter(String value) throws Exception {
        var result = call(get(URL).with(user(new CustomUserDetails(manager))).param("actorId", member.getId().toString()).param("search", value), 200);
        assertThat(resultIds(result)).containsExactly(ids.get(5), ids.get(2), ids.get(1), ids.get(0));
    }

    @Test void rawMetadataNeverSelectedSearchedOrReturned() throws Exception {
        String secret = "BE04-private-sentinel";
        jdbc.update("UPDATE activity_log SET summary=?,details=? WHERE id=?", secret,
                "{\"password\":\"" + secret + "\",\"token\":\"" + secret + "\",\"note\":\"" + "x".repeat(10000) + "\"}", ids.get(1));
        SqlCapture.SQL.get().clear();
        var result = call(request(), 200);
        assertThat(result.toString()).doesNotContain(secret, "192.0.2.4", "passwordHash", "sensitive-json-marker", "Sensitive summary");
        for (var row : result.path("content")) {
            assertThat(row.size()).isEqualTo(9);
            assertThat(row.path("shortDetail").asText()).hasSizeLessThanOrEqualTo(60);
        }
        var auditSql = SqlCapture.SQL.get().stream().filter(s -> s.contains("from activity_log")).toList();
        assertThat(auditSql).hasSize(2);
        assertThat(auditSql).allSatisfy(sql -> assertThat(sql).doesNotContain("password_hash", "details", "summary", "ip_address", "email", "phone"));
        assertThat(auditSql.getFirst()).contains("offset").containsPattern("fetch (first|next)");
        assertThat(resultIds(call(get(URL).with(user(new CustomUserDetails(manager))).param("search", secret), 200))).isEmpty();
    }

    @Test void nullAndLegacyMetadataAndHistoricalActionRemainReadable() throws Exception {
        jdbc.update("UPDATE activity_log SET summary=NULL,details=NULL WHERE id=?", ids.get(1));
        long legacy = event(null, "OLD_EVENT", DAY + "T23:00:00", 987L);
        var result = call(request(), 200);
        var row = StreamSupport.stream(result.path("content").spliterator(), false)
                .filter(r -> r.path("id").asLong() == legacy).findFirst().orElseThrow();
        assertThat(row.path("action").asText()).isEqualTo("OLD_EVENT");
        assertThat(row.path("shortDetail").asText()).isEqualTo("Activity recorded");
    }

    @ParameterizedTest @CsvSource({"from,2026-02-30", "to,invalid", "action,UNKNOWN", "page,-1", "size,0", "size,101", "actorId,0"})
    void invalidHttpParameters(String field, String value) throws Exception {
        var result = call(request().param(field, value), 400);
        assertThat(result.path("status").asInt()).isEqualTo(400);
        assertThat(result.has("timestamp")).isTrue();
    }

    @Test void largeDatasetUsesTwoBoundedScalarQueries() throws Exception {
        for (int index = 0; index < 205; index++) event(member, "USER_UPDATED", DAY + "T12:00:00", (long)index);
        em.clear(); SqlCapture.SQL.get().clear();
        var result = call(request().param("action", "USER_UPDATED").param("size", "20").param("page", "5"), 200);
        assertThat(resultIds(result)).hasSize(20);
        assertThat(result.path("totalElements").asInt()).isEqualTo(205);
        assertThat(result.path("totalPages").asInt()).isEqualTo(11);
        assertThat(SqlCapture.SQL.get().stream().filter(s -> s.contains("from activity_log"))).hasSize(2);
        assertThat(SqlCapture.SQL.get()).hasSize(2);
    }

    @Test void readsDoNotWriteAndAuditServiceStillWorks() throws Exception {
        var before = jdbc.queryForList("SELECT * FROM activity_log WHERE entity_type=? ORDER BY id", marker);
        Long globalBefore = jdbc.queryForObject("SELECT COUNT_BIG(*) FROM activity_log", Long.class);
        call(request(), 200); call(request().param("page", "1").param("size", "2"), 200);
        assertThat(jdbc.queryForList("SELECT * FROM activity_log WHERE entity_type=? ORDER BY id", marker)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM activity_log", Long.class)).isEqualTo(globalBefore);
        audit.record(AuditEvent.builder().actor(users.getReferenceById(member.getId())).action(AuditAction.USER_UPDATED)
                .entityType(marker).entityId(777L).summary("Existing writer remains intact").details(Map.of("safe", "value")).build());
        em.flush(); em.clear();
        assertThat(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM activity_log WHERE entity_type=?", Long.class, marker)).isEqualTo(7);
        var result = call(request().param("action", "USER_UPDATED"), 200);
        assertThat(result.path("content").get(0).path("entityId").asLong()).isEqualTo(777);
        assertThat(jdbc.queryForObject("SELECT JSON_VALUE(details,'$.safe') FROM activity_log WHERE entity_type=? AND entity_id=777", String.class, marker)).isEqualTo("value");
    }
}
