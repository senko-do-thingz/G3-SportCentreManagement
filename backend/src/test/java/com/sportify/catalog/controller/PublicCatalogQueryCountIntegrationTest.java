package com.sportify.catalog.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sportify.catalog.dto.SportPackageResponse;
import com.sportify.catalog.entity.SportPackage;
import com.sportify.catalog.entity.TrainingFormat;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MSSQLServerContainer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real SQL Server, real MVC/security/services/mappers, and a cold persistence context per request. */
@Tag("integration")
@SpringBootTest(properties = {"spring.jpa.properties.hibernate.generate_statistics=true", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(PublicCatalogQueryCountIntegrationTest.QueryConfiguration.class)
class PublicCatalogQueryCountIntegrationTest {
    private static MSSQLServerContainer<?> container;
    private static final ThreadLocal<List<String>> CAPTURE = new ThreadLocal<>();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @Autowired ObjectMapper mapper;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String local = System.getenv("BE_MAINT_TEST_DB_URL");
        if (local != null) {
            if (!local.matches("jdbc:sqlserver://(localhost|127\\.0\\.0\\.1)(:[0-9]+)?;.*databaseName=be_maint_01_[a-zA-Z0-9_]+(;.*)?")) {
                throw new IllegalArgumentException("Local probe requires an isolated BE-MAINT database on localhost");
            }
            properties.add("spring.datasource.url", () -> local);
            properties.add("spring.datasource.username", () -> System.getenv("BE_MAINT_TEST_DB_USERNAME"));
            properties.add("spring.datasource.password", () -> System.getenv("BE_MAINT_TEST_DB_PASSWORD"));
        } else {
            container = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
                    .acceptLicense().withPassword("Probe_Only_Password_2026!");
            container.start();
            properties.add("spring.datasource.url", () -> container.getJdbcUrl() + ";encrypt=false;trustServerCertificate=true");
            properties.add("spring.datasource.username", container::getUsername);
            properties.add("spring.datasource.password", container::getPassword);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 20})
    void publicRequestsHaveBoundedSqlAndEquivalentResponses(int size) throws Exception {
        Fixture fixture = fixture(size, false);
        long packageQueries = -1;
        for (String endpoint : List.of("/sports", "/facilities/public", "/coaches/public", "/packages", "/membership-cards/tiers")) {
            List<Double> millis = new ArrayList<>();
            Long expectedCount = null;
            String expectedHash = null;
            for (int repeat = 0; repeat < 5; repeat++) {
                em.clear();
                var stats = emf.unwrap(SessionFactory.class).getStatistics();
                stats.clear();
                List<String> sql = new ArrayList<>();
                CAPTURE.set(sql);
                long start = System.nanoTime();
                JsonNode body;
                try {
                    var result = mvc.perform(get("/api/v1" + endpoint)).andExpect(status().isOk()).andReturn();
                    body = mapper.readTree(result.getResponse().getContentAsByteArray());
                } finally { CAPTURE.remove(); }
                millis.add((System.nanoTime() - start) / 1_000_000.0);
                assertEquals(size, body.size(), endpoint);
                assertEquals(sql.size(), stats.getPrepareStatementCount());
                if (repeat == 0) sql.stream().distinct().forEach(statement ->
                        System.out.println("BE_MAINT_SQL " + endpoint + " size=" + size + " " + statement));
                if (expectedCount == null) expectedCount = stats.getPrepareStatementCount();
                assertEquals(expectedCount.longValue(), stats.getPrepareStatementCount());
                if (endpoint.equals("/packages")) {
                    assertEquals(mapper.readTree(mapper.writeValueAsBytes(fixture.packages)), body);
                    packageQueries = expectedCount;
                } else if (endpoint.equals("/coaches/public")) {
                    assertEquals(2, expectedCount);
                    var expectedNames = java.util.stream.IntStream.range(0, size).boxed()
                            .sorted(Comparator.comparingInt((Integer i) -> i % 3).thenComparingInt(i -> i))
                            .map(i -> "Probe Coach %02d".formatted(i)).toList();
                    assertEquals(expectedNames, body.findValuesAsText("fullName"));
                    for (JsonNode coach : body) {
                        Set<String> names = new java.util.HashSet<>();
                        coach.fieldNames().forEachRemaining(names::add);
                        assertEquals(Set.of("id", "fullName", "headline", "specialties", "yearsExperience", "bio", "avatarUrl", "sportIds"), names);
                        assertEquals(1, coach.get("sportIds").size());
                    }
                    assertEquals(1, sql.stream().filter(q -> q.contains(" from coach_sport ")).count());
                    assertEquals(0, sql.stream().filter(q -> q.contains(" from sport ")).count());
                } else assertEquals(1, expectedCount, endpoint);
                String hash = hash(normalize(body, endpoint, fixture));
                if (expectedHash == null) expectedHash = hash;
                assertEquals(expectedHash, hash, "Repeated cold reads preserve ordering and full response content");
            }
            millis.sort(Double::compareTo);
            String row = String.format(Locale.ROOT, "%s,%d,%d,%.3f,%s%n", endpoint, size, expectedCount, millis.get(2), expectedHash);
            System.out.print("BE_MAINT_QUERY " + row);
            String metrics = System.getProperty("beMaint.metricsFile");
            if (metrics != null) Files.writeString(Path.of(metrics), row, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        assertEquals(1, packageQueries, "Active package DTO mapping must not perform one sport read per distinct sport");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 20})
    void unfetchedLegacyReadIsADeterministicScalingNegativeControl(int size) {
        fixture(size, false);
        em.clear();
        List<String> sql = new ArrayList<>();
        CAPTURE.set(sql);
        try {
            em.createQuery("select p from SportPackage p where p.isActive = true", SportPackage.class)
                    .getResultList().forEach(p -> assertNotNull(p.getSport().getName()));
        } finally { CAPTURE.remove(); }
        assertEquals(size + 1, sql.size(), "Cold distinct related sports expose the original un-fetched query shape");
        System.out.println("BE_MAINT_LEGACY size=" + size + " queries=" + sql.size());
    }

    @Test
    void twentyPackagesSharingOneSportDoNotPretendToBeTwentyDistinctSportReads() throws Exception {
        Fixture f = fixture(20, true);
        em.clear();
        List<String> sql = new ArrayList<>();
        CAPTURE.set(sql);
        try {
            JsonNode body = mapper.readTree(mvc.perform(get("/api/v1/packages")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray());
            assertEquals(mapper.readTree(mapper.writeValueAsBytes(f.packages)), body);
        } finally { CAPTURE.remove(); }
        System.out.println("BE_MAINT_SHARED_SPORT queries=" + sql.size());
        assertEquals(1, sql.size());
    }

    @Test
    void emptyCataloguesAndMultipurposeFacilitiesKeepTheirContracts() throws Exception {
        Fixture f = fixture(1, false);
        jdbc.update("update sport_package set is_active=0");
        jdbc.update("update user_account set status='DISABLED' where role_id=(select id from role where code='COACH')");
        em.clear();
        assertEquals("[]", mvc.perform(get("/api/v1/packages")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        em.clear();
        assertEquals("[]", mvc.perform(get("/api/v1/coaches/public")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        jdbc.update("update facility set sport_id=null where id=?", f.facilityIds.getFirst());
        em.clear();
        JsonNode facilities = mapper.readTree(mvc.perform(get("/api/v1/facilities/public")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        assertTrue(facilities.get(0).get("sportId").isNull());
    }

    private Fixture fixture(int size, boolean sharedSport) {
        jdbc.update("update sport_package set is_active=0");
        jdbc.update("update facility set is_active=0");
        jdbc.update("update sport set is_active=0");
        jdbc.update("update membership_card_tier set is_active=0");
        jdbc.update("update user_account set status='DISABLED' where role_id=(select id from role where code='COACH')");
        Fixture f = new Fixture();
        Long role = jdbc.queryForObject("select id from role where code='COACH'", Long.class);
        for (int i = 0; i < size; i++) {
            String suffix = "%02d".formatted(i);
            Long sport = insert("insert into sport(code,name,venue_type,display_order) output inserted.id values(?,?,'INDOOR',?)", "PROBE_S_" + suffix, "Probe Sport " + suffix, i);
            f.sportIds.add(sport);
            Long coach = coach(role, "Probe Coach " + suffix, "probe-" + suffix, sport, true, true, i % 3);
            f.coachIds.add(coach);
            f.facilityIds.add(insert("insert into facility(code,name,facility_type,sport_id,capacity) output inserted.id values(?,?,'COURT',?,10)", "PROBE_F_" + suffix, "Probe Facility " + suffix, sport));
            f.tierIds.add(insert("insert into membership_card_tier(code,name,price,duration_months,discount_percentage) output inserted.id values(?,?,100000,1,5)", "PROBE_T_" + suffix, "Probe Tier " + suffix));
            Long packageSport = sharedSport ? f.sportIds.getFirst() : sport;
            Long pkg = insert("insert into sport_package(code,name,sport_id,training_format,duration_days,session_count,session_minutes,price_amount) output inserted.id values(?,?,?,'SELF_TRAINING',30,8,60,100000)", "PROBE_P_" + suffix, "Probe Package " + suffix, packageSport);
            f.packages.add(SportPackageResponse.builder().id(pkg).code("PROBE_P_" + suffix).name("Probe Package " + suffix)
                    .sportId(packageSport).sportName("Probe Sport " + (sharedSport ? "00" : suffix)).sportCode("PROBE_S_" + (sharedSport ? "00" : suffix))
                    .trainingFormat(TrainingFormat.SELF_TRAINING).durationDays(30).sessionCount(8).sessionMinutes(60)
                    .priceAmount(new BigDecimal("100000.00")).isActive(true).build());
        }
        coach(role, "Probe Hidden", "probe-hidden", f.sportIds.getFirst(), false, true, 0);
        coach(role, "Probe Disabled", "probe-disabled", f.sportIds.getFirst(), true, false, 0);
        insert("insert into sport_package(code,name,sport_id,training_format,duration_days,session_count,session_minutes,price_amount,is_active) output inserted.id values('PROBE_INACTIVE','Probe inactive',?,'SELF_TRAINING',30,8,60,100000,0)", f.sportIds.getFirst());
        return f;
    }

    private Long coach(Long role, String name, String key, Long sport, boolean visible, boolean active, int order) {
        Long id = insert("insert into user_account(email,password_hash,full_name,role_id,status) output inserted.id values(?,'UNUSABLE_TEST_FIXTURE',?,?,?)", key + "@example.test", name, role, active ? "ACTIVE" : "DISABLED");
        jdbc.update("insert into coach_profile(user_id,years_experience,is_public,display_order) values(?,5,?,?)", id, visible, order);
        jdbc.update("insert into coach_sport(coach_id,sport_id) values(?,?)", id, sport);
        return id;
    }
    private Long insert(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }

    private JsonNode normalize(JsonNode original, String endpoint, Fixture fixture) {
        ArrayNode body = original.deepCopy();
        List<Long> ids = switch (endpoint) {
            case "/sports" -> fixture.sportIds;
            case "/coaches/public" -> fixture.coachIds;
            case "/facilities/public" -> fixture.facilityIds;
            case "/membership-cards/tiers" -> fixture.tierIds;
            default -> fixture.packages.stream().map(SportPackageResponse::getId).toList();
        };
        for (JsonNode row : body) {
            ObjectNode object = (ObjectNode) row;
            object.put("id", ids.indexOf(row.get("id").asLong()) + 1);
            if (row.hasNonNull("sportId")) object.put("sportId", fixture.sportIds.indexOf(row.get("sportId").asLong()) + 1);
            if (row.has("sportIds")) {
                ArrayNode sports = mapper.createArrayNode();
                row.get("sportIds").forEach(id -> sports.add(fixture.sportIds.indexOf(id.asLong()) + 1));
                object.set("sportIds", sports);
            }
        }
        return body;
    }
    private String hash(JsonNode node) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsString(node).getBytes(StandardCharsets.UTF_8)));
    }
    private static class Fixture {
        final List<Long> sportIds = new ArrayList<>(), coachIds = new ArrayList<>(), facilityIds = new ArrayList<>(), tierIds = new ArrayList<>();
        final List<SportPackageResponse> packages = new ArrayList<>();
    }
    @TestConfiguration
    static class QueryConfiguration {
        @Bean HibernatePropertiesCustomizer queryInspector() {
            return properties -> properties.put(AvailableSettings.STATEMENT_INSPECTOR, (StatementInspector) sql -> {
                List<String> active = CAPTURE.get();
                if (active != null) active.add(sql);
                return sql;
            });
        }
    }
}
