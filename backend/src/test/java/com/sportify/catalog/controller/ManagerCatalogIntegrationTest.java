package com.sportify.catalog.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.dto.MemberCardPurchaseRequest;
import com.sportify.catalog.dto.MemberCardResponse;
import com.sportify.catalog.dto.MembershipCardTierUpdateRequest;
import com.sportify.catalog.dto.SportPackageResponse;
import com.sportify.catalog.dto.SportPackageUpdateRequest;
import com.sportify.catalog.entity.CardStatus;
import com.sportify.catalog.entity.MemberCard;
import com.sportify.catalog.entity.MembershipCardTier;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.RegistrationChannel;
import com.sportify.catalog.entity.Sport;
import com.sportify.catalog.entity.SportPackage;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.entity.TrainingFormat;
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.MembershipCardTierRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.catalog.repository.SportPackageRepository;
import com.sportify.catalog.repository.SportPackageSpecifications;
import com.sportify.catalog.repository.SportRepository;
import com.sportify.catalog.service.MembershipCardService;
import com.sportify.catalog.service.SportPackageService;
import com.sportify.core.audit.AuditAction;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.identity.entity.ActivityLog;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.ActivityLogRepository;
import com.sportify.identity.repository.MemberProfileRepository;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.security.JwtService;
import com.sportify.payment.entity.PaymentMethod;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for task BE-03 Manager Catalog and Pricing features.
 * Extends AbstractIntegrationTest. Requires Docker and Testcontainers to execute.
 * Runs in CI (GitHub Actions) where Docker is available; skipped locally when Docker is unavailable.
 */
@Tag("integration")
@AutoConfigureMockMvc
class ManagerCatalogIntegrationTest extends AbstractIntegrationTest {

    private static final String MARKER = "be03it";

    @Value("${spring.flyway.locations}")
    private String flywayLocations;

    private String[] getFlywayLocationArray() {
        return (flywayLocations != null && !flywayLocations.isBlank())
                ? flywayLocations.split(",")
                : new String[]{"classpath:db/migration"};
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SportPackageRepository sportPackageRepository;

    @Autowired
    private SportPackageRegistrationRepository registrationRepository;

    @Autowired
    private MembershipCardTierRepository cardTierRepository;

    @Autowired
    private MemberCardRepository memberCardRepository;

    @Autowired
    private SportRepository sportRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private MemberProfileRepository memberProfileRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private SportPackageService sportPackageService;

    @Autowired
    private MembershipCardService membershipCardService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private Sport activeSport1;
    private Sport activeSport2;
    private UserAccount managerUser;
    private UserAccount memberUser;
    private MemberProfile memberProfile;
    private String managerToken;

    @BeforeEach
    void setUp() {
        cleanUp();

        List<Sport> sports = sportRepository.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .toList();
        assertTrue(sports.size() >= 2, "At least two active sports are required for integration tests");
        activeSport1 = sports.get(0);
        activeSport2 = sports.get(1);

        Role managerRole = roleRepository.findByCode("MANAGER")
                .orElseGet(() -> roleRepository.save(Role.builder().code("MANAGER").name("Manager").build()));
        managerUser = userRepository.save(UserAccount.builder()
                .email("manager_" + MARKER + "@sportify.com")
                .passwordHash("hashed")
                .fullName("Manager BE03")
                .status("ACTIVE")
                .role(managerRole)
                .build());
        managerToken = jwtService.generateToken(new CustomUserDetails(managerUser));

        Role memberRole = roleRepository.findByCode("MEMBER")
                .orElseGet(() -> roleRepository.save(Role.builder().code("MEMBER").name("Member").build()));
        UserAccount memberAccount = userRepository.save(UserAccount.builder()
                .email("member_" + MARKER + "@sportify.com")
                .passwordHash("hashed")
                .fullName("Member BE03")
                .status("ACTIVE")
                .role(memberRole)
                .build());
        memberUser = memberAccount;
        jdbcTemplate.update("INSERT INTO member_profile (user_id, member_code, current_level, joined_on) VALUES (?, ?, 'BEGINNER', ?)",
                memberAccount.getId(), "MEM-" + MARKER, LocalDate.now());
        memberProfile = memberProfileRepository.findById(memberAccount.getId()).orElseThrow();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM sport_package_registration WHERE registration_code LIKE '%" + MARKER + "%' OR created_by_user_id IN (SELECT id FROM user_account WHERE email LIKE '%" + MARKER + "%') OR member_id IN (SELECT user_id FROM member_profile WHERE member_code LIKE '%" + MARKER + "%')");
        jdbcTemplate.update("UPDATE member_card SET replaces_card_id = NULL WHERE card_code LIKE '%" + MARKER + "%' OR member_id IN (SELECT user_id FROM member_profile WHERE member_code LIKE '%" + MARKER + "%')");
        jdbcTemplate.update("UPDATE member_card SET payment_id = NULL WHERE card_code LIKE '%" + MARKER + "%' OR member_id IN (SELECT user_id FROM member_profile WHERE member_code LIKE '%" + MARKER + "%')");
        jdbcTemplate.update("DELETE FROM member_card WHERE card_code LIKE '%" + MARKER + "%' OR member_id IN (SELECT user_id FROM member_profile WHERE member_code LIKE '%" + MARKER + "%')");
        jdbcTemplate.update("DELETE FROM payment WHERE member_id IN (SELECT user_id FROM member_profile WHERE member_code LIKE '%" + MARKER + "%') OR payment_code LIKE '%" + MARKER + "%'");
        jdbcTemplate.update("DELETE FROM activity_log WHERE actor_user_id IN (SELECT id FROM user_account WHERE email LIKE '%" + MARKER + "%') OR summary LIKE '%" + MARKER + "%' OR entity_code LIKE '%" + MARKER + "%' OR details LIKE '%" + MARKER + "%'");
        jdbcTemplate.update("DELETE FROM sport_package WHERE code LIKE '%" + MARKER + "%'");
        jdbcTemplate.update("DELETE FROM membership_card_tier WHERE code LIKE '%" + MARKER + "%'");
        jdbcTemplate.update("DELETE FROM member_profile WHERE member_code LIKE '%" + MARKER + "%'");
        jdbcTemplate.update("DELETE FROM user_account WHERE email LIKE '%" + MARKER + "%'");
    }

    @Test
    @DisplayName("SportPackageSpecifications dynamic filters: sportId, format, active, no filter, and combined")
    void testSportPackageSpecifications_filters() {
        // Create 3 distinct test packages
        SportPackage pkg1 = sportPackageRepository.save(SportPackage.builder()
                .code("PKG-" + MARKER + "-1")
                .name("Test Package 1 " + MARKER)
                .sport(activeSport1)
                .trainingFormat(TrainingFormat.SELF_TRAINING)
                .durationDays(30)
                .sessionCount(10)
                .sessionMinutes(60)
                .priceAmount(BigDecimal.valueOf(100000.00))
                .isActive(true)
                .build());

        SportPackage pkg2 = sportPackageRepository.save(SportPackage.builder()
                .code("PKG-" + MARKER + "-2")
                .name("Test Package 2 " + MARKER)
                .sport(activeSport1)
                .trainingFormat(TrainingFormat.COACH_LED)
                .durationDays(60)
                .sessionCount(20)
                .sessionMinutes(90)
                .priceAmount(BigDecimal.valueOf(250000.00))
                .isActive(false)
                .build());

        SportPackage pkg3 = sportPackageRepository.save(SportPackage.builder()
                .code("PKG-" + MARKER + "-3")
                .name("Test Package 3 " + MARKER)
                .sport(activeSport2)
                .trainingFormat(TrainingFormat.SELF_TRAINING)
                .durationDays(90)
                .sessionCount(30)
                .sessionMinutes(60)
                .priceAmount(BigDecimal.valueOf(400000.00))
                .isActive(true)
                .build());

        // 1. No filter (all nulls)
        Specification<SportPackage> specNoFilter = SportPackageSpecifications.withFilters(null, null, null);
        List<SportPackage> allResults = sportPackageRepository.findAll(specNoFilter);
        assertTrue(allResults.stream().anyMatch(p -> p.getCode().equals(pkg1.getCode())));
        assertTrue(allResults.stream().anyMatch(p -> p.getCode().equals(pkg2.getCode())));
        assertTrue(allResults.stream().anyMatch(p -> p.getCode().equals(pkg3.getCode())));

        // 2. sportId filter
        Specification<SportPackage> specSport1 = SportPackageSpecifications.withFilters(activeSport1.getId(), null, null);
        List<SportPackage> sport1Results = sportPackageRepository.findAll(specSport1);
        assertTrue(sport1Results.stream().allMatch(p -> p.getSport().getId().equals(activeSport1.getId())));
        assertTrue(sport1Results.stream().anyMatch(p -> p.getCode().equals(pkg1.getCode())));
        assertTrue(sport1Results.stream().anyMatch(p -> p.getCode().equals(pkg2.getCode())));
        assertFalse(sport1Results.stream().anyMatch(p -> p.getCode().equals(pkg3.getCode())));

        // 3. format filter (COACH_LED)
        Specification<SportPackage> specCoachLed = SportPackageSpecifications.withFilters(null, TrainingFormat.COACH_LED, null);
        List<SportPackage> coachLedResults = sportPackageRepository.findAll(specCoachLed);
        assertTrue(coachLedResults.stream().allMatch(p -> p.getTrainingFormat() == TrainingFormat.COACH_LED));
        assertTrue(coachLedResults.stream().anyMatch(p -> p.getCode().equals(pkg2.getCode())));
        assertFalse(coachLedResults.stream().anyMatch(p -> p.getCode().equals(pkg1.getCode())));

        // 4. active filter (false)
        Specification<SportPackage> specInactive = SportPackageSpecifications.withFilters(null, null, false);
        List<SportPackage> inactiveResults = sportPackageRepository.findAll(specInactive);
        assertTrue(inactiveResults.stream().allMatch(p -> Boolean.FALSE.equals(p.getIsActive())));
        assertTrue(inactiveResults.stream().anyMatch(p -> p.getCode().equals(pkg2.getCode())));
        assertFalse(inactiveResults.stream().anyMatch(p -> p.getCode().equals(pkg1.getCode())));

        // 5. Combined filter: activeSport1 + SELF_TRAINING + active=true -> matches only pkg1
        Specification<SportPackage> specCombined = SportPackageSpecifications.withFilters(
                activeSport1.getId(), TrainingFormat.SELF_TRAINING, true);
        List<SportPackage> combinedResults = sportPackageRepository.findAll(specCombined);
        assertTrue(combinedResults.stream().anyMatch(p -> p.getCode().equals(pkg1.getCode())));
        assertFalse(combinedResults.stream().anyMatch(p -> p.getCode().equals(pkg2.getCode())));
        assertFalse(combinedResults.stream().anyMatch(p -> p.getCode().equals(pkg3.getCode())));
    }

    @Test
    @DisplayName("Package update persists and writes an activity_log row with before and after details")
    void testPackageUpdate_persistsAndWritesActivityLog() throws Exception {
        SportPackage pkg = sportPackageRepository.save(SportPackage.builder()
                .code("PKG-" + MARKER + "-AUD")
                .name("Audit Test Pkg " + MARKER)
                .sport(activeSport1)
                .trainingFormat(TrainingFormat.SELF_TRAINING)
                .durationDays(30)
                .sessionCount(10)
                .sessionMinutes(60)
                .priceAmount(BigDecimal.valueOf(150000.00))
                .description("Initial description")
                .isActive(true)
                .build());

        SportPackageUpdateRequest request = SportPackageUpdateRequest.builder()
                .name("Updated Audit Pkg " + MARKER)
                .price(BigDecimal.valueOf(220000.00))
                .sessionCount(15)
                .durationDays(45)
                .sessionMinutes(90)
                .active(false)
                .build();

        SportPackageResponse response = sportPackageService.updatePackage(pkg.getId(), request, managerUser);
        assertNotNull(response);
        assertEquals("Updated Audit Pkg " + MARKER, response.getName());
        assertEquals(0, BigDecimal.valueOf(220000.00).compareTo(response.getPriceAmount()));

        // Verify entity persisted in DB
        SportPackage reloaded = sportPackageRepository.findById(pkg.getId()).orElseThrow();
        assertEquals("Updated Audit Pkg " + MARKER, reloaded.getName());
        assertEquals(0, BigDecimal.valueOf(220000.00).compareTo(reloaded.getPriceAmount()));
        assertEquals(15, reloaded.getSessionCount());
        assertEquals(45, reloaded.getDurationDays());
        assertEquals(90, reloaded.getSessionMinutes());
        assertFalse(reloaded.getIsActive());
        assertEquals("Initial description", reloaded.getDescription());

        // Verify activity_log row written with before and after snapshots
        List<ActivityLog> logs = activityLogRepository.findAll().stream()
                .filter(l -> "SPORT_PACKAGE".equals(l.getEntityType()) && pkg.getId().equals(l.getEntityId()))
                .filter(l -> AuditAction.PACKAGE_UPDATED.equals(l.getAction()))
                .toList();
        assertFalse(logs.isEmpty(), "Activity log row must be written for package update");
        ActivityLog log = logs.get(logs.size() - 1);
        assertEquals(managerUser.getId(), log.getActor().getId());

        JsonNode detailsNode = objectMapper.readTree(log.getDetails());
        assertTrue(detailsNode.has("before"));
        assertTrue(detailsNode.has("after"));
        assertEquals(150000.00, detailsNode.get("before").get("priceAmount").asDouble());
        assertEquals(220000.00, detailsNode.get("after").get("priceAmount").asDouble());
        assertEquals(10, detailsNode.get("before").get("sessionCount").asInt());
        assertEquals(15, detailsNode.get("after").get("sessionCount").asInt());
    }

    @Test
    @DisplayName("Tier update persists and writes an activity_log row with before and after details")
    void testTierUpdate_persistsAndWritesActivityLog() throws Exception {
        MembershipCardTier testTier = cardTierRepository.save(MembershipCardTier.builder()
                .code("TIER-UPD-" + MARKER)
                .name("Tier Update " + MARKER)
                .price(BigDecimal.valueOf(700000.00))
                .durationMonths(12)
                .discountPercentage(5)
                .description("Initial description " + MARKER)
                .tierRank(10)
                .isActive(true)
                .build());
        BigDecimal originalPrice = testTier.getPrice();
        Integer originalDiscount = testTier.getDiscountPercentage();

        MembershipCardTierUpdateRequest request = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.valueOf(780000.00))
                .durationMonths(12)
                .discountPercentage(7)
                .description("Updated tier description " + MARKER)
                .active(true)
                .build();

        membershipCardService.updateTier(testTier.getId(), request, managerUser);

        // Verify entity persisted in DB
        MembershipCardTier reloaded = cardTierRepository.findById(testTier.getId()).orElseThrow();
        assertEquals(testTier.getName(), reloaded.getName());
        assertEquals(0, BigDecimal.valueOf(780000.00).compareTo(reloaded.getPrice()));
        assertEquals(7, reloaded.getDiscountPercentage());
        assertEquals("Updated tier description " + MARKER, reloaded.getDescription());

        // Verify activity_log row written with before and after snapshots
        List<ActivityLog> logs = activityLogRepository.findAll().stream()
                .filter(l -> "MEMBERSHIP_CARD_TIER".equals(l.getEntityType()) && testTier.getId().equals(l.getEntityId()))
                .filter(l -> AuditAction.CARD_TIER_UPDATED.equals(l.getAction()))
                .toList();
        assertFalse(logs.isEmpty(), "Activity log row must be written for tier update");
        ActivityLog log = logs.get(logs.size() - 1);
        assertEquals(managerUser.getId(), log.getActor().getId());

        JsonNode detailsNode = objectMapper.readTree(log.getDetails());
        assertTrue(detailsNode.has("before"));
        assertTrue(detailsNode.has("after"));
        assertEquals(originalPrice.doubleValue(), detailsNode.get("before").get("price").asDouble());
        assertEquals(780000.00, detailsNode.get("after").get("price").asDouble());
        assertEquals(originalDiscount.intValue(), detailsNode.get("before").get("discountPercentage").asInt());
        assertEquals(7, detailsNode.get("after").get("discountPercentage").asInt());
    }

    @Test
    @DisplayName("Historical snapshot preservation: updating package and tier prices does not alter existing rows")
    void testHistoricalPriceSnapshotPreservation_whenPricesUpdated() {
        // 1. Create a package and register member
        SportPackage pkg = sportPackageRepository.save(SportPackage.builder()
                .code("PKG-" + MARKER + "-SNAP")
                .name("Snapshot Package " + MARKER)
                .sport(activeSport1)
                .trainingFormat(TrainingFormat.SELF_TRAINING)
                .durationDays(30)
                .sessionCount(10)
                .sessionMinutes(60)
                .priceAmount(BigDecimal.valueOf(500000.00))
                .isActive(true)
                .build());

        SportPackageRegistration registration = registrationRepository.save(SportPackageRegistration.builder()
                .registrationCode("REG-" + MARKER)
                .member(memberProfile)
                .sportPackage(pkg)
                .channel(RegistrationChannel.ONLINE)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(30))
                .totalSessions(10)
                .remainingSessions(10)
                .originalPrice(BigDecimal.valueOf(500000.00))
                .discountPercentage(0)
                .paidAmount(BigDecimal.valueOf(500000.00))
                .status(PackageRegistrationStatus.ACTIVE)
                .createdByUser(managerUser)
                .build());

        // 2. Create an active member card for test tier
        MembershipCardTier testTier = cardTierRepository.save(MembershipCardTier.builder()
                .code("TIER-SNAP-" + MARKER)
                .name("Snapshot Tier " + MARKER)
                .price(BigDecimal.valueOf(800000.00))
                .durationMonths(12)
                .discountPercentage(5)
                .tierRank(11)
                .isActive(true)
                .build());

        MemberCard card = memberCardRepository.save(MemberCard.builder()
                .cardCode("CARD-" + MARKER)
                .member(memberProfile)
                .tier(testTier)
                .pricePaid(BigDecimal.valueOf(800000.00))
                .status(CardStatus.ACTIVE)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(12))
                .build());

        // 3. Manager updates package price to 750,000
        SportPackageUpdateRequest packageUpdateReq = SportPackageUpdateRequest.builder()
                .name("Snapshot Package " + MARKER)
                .price(BigDecimal.valueOf(750000.00))
                .sessionCount(10)
                .durationDays(30)
                .sessionMinutes(60)
                .active(true)
                .build();
        sportPackageService.updatePackage(pkg.getId(), packageUpdateReq, managerUser);

        // 4. Manager updates tier price to 850,000
        MembershipCardTierUpdateRequest tierUpdateReq = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.valueOf(850000.00))
                .durationMonths(testTier.getDurationMonths())
                .discountPercentage(testTier.getDiscountPercentage())
                .active(true)
                .build();
        membershipCardService.updateTier(testTier.getId(), tierUpdateReq, managerUser);

        // 5. Assert existing registration still retains snapshot price in DB
        SportPackageRegistration reloadedReg = registrationRepository.findById(registration.getId()).orElseThrow();
        assertEquals(0, BigDecimal.valueOf(500000.00).compareTo(reloadedReg.getOriginalPrice()),
                "Historical originalPrice must remain 500000");
        assertEquals(0, BigDecimal.valueOf(500000.00).compareTo(reloadedReg.getPaidAmount()),
                "Historical paidAmount must remain 500000");
        assertEquals(0, reloadedReg.getDiscountPercentage());

        // 6. Assert existing member card still retains snapshot pricePaid in DB
        MemberCard reloadedCard = memberCardRepository.findById(card.getId()).orElseThrow();
        assertEquals(0, BigDecimal.valueOf(800000.00).compareTo(reloadedCard.getPricePaid()),
                "Historical pricePaid must remain 800000");
    }

    @Test
    @DisplayName("Flyway applies all migrations including V22: member_card has upgrade_request and replaces_card_id")
    void testFlywayAppliesV22_memberCardColumnsExist() {
        Integer upgradeColCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys.columns c JOIN sys.tables t ON t.object_id = c.object_id WHERE t.name = 'member_card' AND c.name = 'upgrade_request'",
                Integer.class
        );
        assertEquals(1, upgradeColCount, "Column upgrade_request must exist on member_card table");

        Integer replacesColCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys.columns c JOIN sys.tables t ON t.object_id = c.object_id WHERE t.name = 'member_card' AND c.name = 'replaces_card_id'",
                Integer.class
        );
        assertEquals(1, replacesColCount, "Column replaces_card_id must exist on member_card table");

        Integer fkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys.foreign_keys fk JOIN sys.tables t ON t.object_id = fk.parent_object_id WHERE t.name = 'member_card' AND fk.name = 'fk_member_card_replaces_card'",
                Integer.class
        );
        assertEquals(1, fkCount, "Foreign key fk_member_card_replaces_card must exist on member_card");

        Integer tierRankColCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys.columns c JOIN sys.tables t ON t.object_id = c.object_id WHERE t.name = 'membership_card_tier' AND c.name = 'tier_rank'",
                Integer.class
        );
        assertEquals(1, tierRankColCount, "Column tier_rank must exist on membership_card_tier table");

        Integer filteredIdxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys.indexes i JOIN sys.tables t ON t.object_id = i.object_id WHERE t.name = 'member_card' AND i.name = 'ux_member_card_replaces_in_force'",
                Integer.class
        );
        assertEquals(1, filteredIdxCount, "Index ux_member_card_replaces_in_force must exist on member_card");
    }

    @Test
    @DisplayName("PENDING_PAYMENT card created, manager raises tier price, activation succeeds and card keeps old pricePaid")
    void testPendingCardActivation_afterTierPriceRaise_retainsOldPricePaid() {
        MembershipCardTier testTier = cardTierRepository.save(MembershipCardTier.builder()
                .code("TIER-PEND-" + MARKER)
                .name("Pending Tier " + MARKER)
                .price(BigDecimal.valueOf(900000.00))
                .durationMonths(12)
                .discountPercentage(5)
                .tierRank(12)
                .isActive(true)
                .build());
        BigDecimal originalPrice = testTier.getPrice();

        // 1. Member creates pending card at original price
        MemberCardPurchaseRequest purchaseReq = MemberCardPurchaseRequest.builder()
                .memberId(memberProfile.getId())
                .tierId(testTier.getId())
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .build();
        MemberCardResponse pendingCard = membershipCardService.purchaseCard(purchaseReq, memberUser);
        assertNotNull(pendingCard);
        assertEquals(CardStatus.PENDING_PAYMENT, pendingCard.getStatus());
        assertEquals(0, originalPrice.compareTo(pendingCard.getPricePaid()));

        // 2. Manager raises tier price by 150k
        BigDecimal raisedPrice = originalPrice.add(BigDecimal.valueOf(150000.00));
        MembershipCardTierUpdateRequest tierUpdateReq = MembershipCardTierUpdateRequest.builder()
                .price(raisedPrice)
                .durationMonths(testTier.getDurationMonths())
                .discountPercentage(testTier.getDiscountPercentage())
                .active(true)
                .build();
        membershipCardService.updateTier(testTier.getId(), tierUpdateReq, managerUser);

        // 3. Manager activates card at desk
        MemberCardResponse activatedCard = membershipCardService.activateCard(pendingCard.getId(), managerUser);
        assertNotNull(activatedCard);
        assertEquals(CardStatus.ACTIVE, activatedCard.getStatus());
        assertEquals(0, originalPrice.compareTo(activatedCard.getPricePaid()),
                "Card must retain old pricePaid snapshot even after tier price increase");

        // Verify in DB
        MemberCard reloaded = memberCardRepository.findById(pendingCard.getId()).orElseThrow();
        assertEquals(CardStatus.ACTIVE, reloaded.getStatus());
        assertEquals(0, originalPrice.compareTo(reloaded.getPricePaid()));
    }

    @Test
    @DisplayName("Upgrade chain A -> B -> C: fee for C is based on cumulative pricePaid")
    void testUpgradeChain_ABC_cumulativePricePaid() {
        // Create 3 tiers for the test
        MembershipCardTier tierA = cardTierRepository.save(MembershipCardTier.builder()
                .code("TIER-A-" + MARKER)
                .name("Tier A " + MARKER)
                .price(BigDecimal.valueOf(150000.00))
                .durationMonths(12)
                .discountPercentage(2)
                .tierRank(21)
                .isActive(true)
                .build());

        MembershipCardTier tierB = cardTierRepository.save(MembershipCardTier.builder()
                .code("TIER-B-" + MARKER)
                .name("Tier B " + MARKER)
                .price(BigDecimal.valueOf(350000.00))
                .durationMonths(12)
                .discountPercentage(5)
                .tierRank(22)
                .isActive(true)
                .build());

        MembershipCardTier tierC = cardTierRepository.save(MembershipCardTier.builder()
                .code("TIER-C-" + MARKER)
                .name("Tier C " + MARKER)
                .price(BigDecimal.valueOf(650000.00))
                .durationMonths(12)
                .discountPercentage(10)
                .tierRank(23)
                .isActive(true)
                .build());

        // 1. Purchase Card A at desk (immediately ACTIVE, pricePaid = 150k)
        MemberCardPurchaseRequest reqA = MemberCardPurchaseRequest.builder()
                .memberId(memberProfile.getId())
                .tierId(tierA.getId())
                .build();
        MemberCardResponse cardA = membershipCardService.purchaseCard(reqA, managerUser);
        assertEquals(CardStatus.ACTIVE, cardA.getStatus());
        assertEquals(0, BigDecimal.valueOf(150000.00).compareTo(cardA.getPricePaid()));

        // 2. Upgrade to Tier B at desk (difference = 350k - 150k = 200k)
        MemberCardPurchaseRequest reqB = MemberCardPurchaseRequest.builder()
                .memberId(memberProfile.getId())
                .tierId(tierB.getId())
                .build();
        MemberCardResponse cardB = membershipCardService.purchaseCard(reqB, managerUser);
        assertEquals(CardStatus.ACTIVE, cardB.getStatus());
        assertEquals(0, BigDecimal.valueOf(200000.00).compareTo(cardB.getPricePaid()));

        // Verify Card A is REPLACED
        MemberCard reloadedA = memberCardRepository.findById(cardA.getId()).orElseThrow();
        assertEquals(CardStatus.REPLACED, reloadedA.getStatus());

        // 3. Upgrade to Tier C at desk (fee = 650k - (150k + 200k) = 300k)
        MemberCardPurchaseRequest reqC = MemberCardPurchaseRequest.builder()
                .memberId(memberProfile.getId())
                .tierId(tierC.getId())
                .build();
        MemberCardResponse cardC = membershipCardService.purchaseCard(reqC, managerUser);
        assertEquals(CardStatus.ACTIVE, cardC.getStatus());
        assertEquals(0, BigDecimal.valueOf(300000.00).compareTo(cardC.getPricePaid()),
                "Upgrade fee for C must be target price (650k) minus cumulative chain paid (150k + 200k = 350k) = 300k");

        // Verify Card B is REPLACED and Card C replaces Card B
        MemberCard reloadedB = memberCardRepository.findById(cardB.getId()).orElseThrow();
        assertEquals(CardStatus.REPLACED, reloadedB.getStatus());
        MemberCard reloadedC = memberCardRepository.findById(cardC.getId()).orElseThrow();
        assertNotNull(reloadedC.getReplacesCard());
        assertEquals(cardB.getId(), reloadedC.getReplacesCard().getId());
    }

    @Test
    @DisplayName("Upgrade after price edit: fee is based on pricePaid, not on live price of held tier")
    void testUpgradeAfterPriceEdit_feeBasedOnPricePaidNotLivePrice() {
        MembershipCardTier silverTier = cardTierRepository.save(MembershipCardTier.builder()
                .code("SILVER-" + MARKER)
                .name("Silver " + MARKER)
                .price(BigDecimal.valueOf(1100000.00))
                .durationMonths(12)
                .discountPercentage(5)
                .tierRank(31)
                .isActive(true)
                .build());

        MembershipCardTier goldTier = cardTierRepository.save(MembershipCardTier.builder()
                .code("GOLD-" + MARKER)
                .name("Gold " + MARKER)
                .price(BigDecimal.valueOf(1400000.00))
                .durationMonths(12)
                .discountPercentage(10)
                .tierRank(32)
                .isActive(true)
                .build());

        // 1. Member purchases Silver card at 1100k
        MemberCardPurchaseRequest silverReq = MemberCardPurchaseRequest.builder()
                .memberId(memberProfile.getId())
                .tierId(silverTier.getId())
                .build();
        MemberCardResponse silverCard = membershipCardService.purchaseCard(silverReq, managerUser);
        assertEquals(CardStatus.ACTIVE, silverCard.getStatus());
        assertEquals(0, BigDecimal.valueOf(1100000.00).compareTo(silverCard.getPricePaid()));

        // 2. Manager raises Silver tier price to 1,250,000
        MembershipCardTierUpdateRequest silverUpdate = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.valueOf(1250000.00))
                .durationMonths(12)
                .discountPercentage(5)
                .active(true)
                .build();
        membershipCardService.updateTier(silverTier.getId(), silverUpdate, managerUser);

        // 3. Member requests upgrade to Gold (1,400,000)
        MemberCardPurchaseRequest goldReq = MemberCardPurchaseRequest.builder()
                .memberId(memberProfile.getId())
                .tierId(goldTier.getId())
                .build();
        MemberCardResponse goldCard = membershipCardService.purchaseCard(goldReq, managerUser);

        // Upgrade fee must be 1400k - 1100k (pricePaid) = 300k, NOT 1400k - 1250k (live) = 150k
        assertEquals(0, BigDecimal.valueOf(300000.00).compareTo(goldCard.getPricePaid()),
                "Upgrade fee must be target tier price (1400k) minus actual pricePaid (1100k) = 300k");
    }

    @Test
    @DisplayName("After manager edit of paid tier, member purchase produces PENDING_PAYMENT card requiring payment")
    void testPurchaseAfterManagerEditOfPaidTier_producesPendingPaymentCardRequiringPayment() {
        MembershipCardTier testTier = cardTierRepository.save(MembershipCardTier.builder()
                .code("TIER-EDIT-" + MARKER)
                .name("Edit Tier " + MARKER)
                .price(BigDecimal.valueOf(1500000.00))
                .durationMonths(12)
                .discountPercentage(5)
                .tierRank(41)
                .isActive(true)
                .build());

        // 1. Manager edits tier price and duration
        MembershipCardTierUpdateRequest updateReq = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.valueOf(1550000.00))
                .durationMonths(12)
                .discountPercentage(7)
                .active(true)
                .build();
        membershipCardService.updateTier(testTier.getId(), updateReq, managerUser);

        // 2. Member purchases card online
        MemberCardPurchaseRequest purchaseReq = MemberCardPurchaseRequest.builder()
                .memberId(memberProfile.getId())
                .tierId(testTier.getId())
                .paymentMethod(PaymentMethod.VNPAY)
                .build();
        MemberCardResponse response = membershipCardService.purchaseCard(purchaseReq, memberUser);

        // Must NOT be immediately active; must be PENDING_PAYMENT with linked payment created
        assertNotNull(response);
        assertEquals(CardStatus.PENDING_PAYMENT, response.getStatus());
        assertEquals(0, BigDecimal.valueOf(1550000.00).compareTo(response.getPricePaid()));
        assertNotNull(response.getPaymentId(), "Pending payment card must have linked payment created");
    }

    @Test
    @DisplayName("Tier rank rule: member holds SILVER paid 1750k, GOLD live price 1700k, requests GOLD -> fee <= 0 free upgrade")
    void testRankRule_memberHoldsSilverPaid550k_goldLive500k_requestsGold() {
        // Create Silver (rank 51) at 1750k and Gold (rank 52) at 1800k
        MembershipCardTier silverTier = cardTierRepository.save(MembershipCardTier.builder()
                .code("SILVER-RR-" + MARKER)
                .name("Silver RR " + MARKER)
                .price(BigDecimal.valueOf(1750000.00))
                .durationMonths(12)
                .discountPercentage(5)
                .tierRank(51)
                .isActive(true)
                .build());

        MembershipCardTier goldTier = cardTierRepository.save(MembershipCardTier.builder()
                .code("GOLD-RR-" + MARKER)
                .name("Gold RR " + MARKER)
                .price(BigDecimal.valueOf(1800000.00))
                .durationMonths(12)
                .discountPercentage(10)
                .tierRank(52)
                .isActive(true)
                .build());

        // 1. Member purchases Silver card at 1750k at desk -> ACTIVE
        MemberCardPurchaseRequest silverPurchase = MemberCardPurchaseRequest.builder()
                .memberId(memberProfile.getId())
                .tierId(silverTier.getId())
                .build();
        MemberCardResponse activeSilver = membershipCardService.purchaseCard(silverPurchase, managerUser);
        assertEquals(CardStatus.ACTIVE, activeSilver.getStatus());
        assertEquals(0, BigDecimal.valueOf(1750000.00).compareTo(activeSilver.getPricePaid()));

        // 2. Manager lowers Silver tier to 1650k and Gold tier to 1700k
        MembershipCardTierUpdateRequest silverUpdate = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.valueOf(1650000.00))
                .durationMonths(12)
                .discountPercentage(5)
                .active(true)
                .build();
        membershipCardService.updateTier(silverTier.getId(), silverUpdate, managerUser);

        MembershipCardTierUpdateRequest goldUpdate = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.valueOf(1700000.00))
                .durationMonths(12)
                .discountPercentage(10)
                .active(true)
                .build();
        membershipCardService.updateTier(goldTier.getId(), goldUpdate, managerUser);

        // 3. Member attempts self-service upgrade to Gold online (diff <= 0).
        // Under B5 fail-closed policy, self-service zero-fee upgrade is rejected.
        MemberCardPurchaseRequest goldUpgrade = MemberCardPurchaseRequest.builder()
                .memberId(memberProfile.getId())
                .tierId(goldTier.getId())
                .build();
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> membershipCardService.purchaseCard(goldUpgrade, memberUser));
        assertEquals("Zero-fee upgrades cannot be completed online. Please contact the front desk.", ex.getMessage());

        // 4. Staff processes the zero-fee upgrade at the desk -> allowed and activates immediately
        MemberCardResponse goldCard = membershipCardService.purchaseCard(goldUpgrade, managerUser);

        assertNotNull(goldCard);
        assertEquals(CardStatus.ACTIVE, goldCard.getStatus(), "Desk fee <= 0 upgrade must activate immediately");
        assertEquals(0, BigDecimal.ZERO.compareTo(goldCard.getPricePaid()), "Upgrade fee must be 0.00");

        // Verify in DB that upgrade tracking fields are set
        MemberCard reloadedGold = memberCardRepository.findById(goldCard.getId()).orElseThrow();
        assertTrue(reloadedGold.isUpgradeRequest(), "Gold card must be marked as upgrade request");
        assertNotNull(reloadedGold.getReplacesCard(), "Gold card must link to replaced card");
        assertEquals(activeSilver.getId(), reloadedGold.getReplacesCard().getId(), "Must replace the active Silver card");

        // Verify Silver card is now REPLACED
        MemberCard reloadedSilver = memberCardRepository.findById(activeSilver.getId()).orElseThrow();
        assertEquals(CardStatus.REPLACED, reloadedSilver.getStatus());

    }

    @Test
    @DisplayName("Migration V21 to V22: programmatically migrate from V21, seed legacy rows, migrate to V22, assert backfill and idempotency")
    void testMigrationV22_backfillUpgradeChainAndIdempotency() throws Exception {
        String testDbName = "sportify_mig_v22_" + System.currentTimeMillis();
        jdbcTemplate.execute("CREATE DATABASE " + testDbName);

        String masterUrl = MSSQL_SERVER_CONTAINER.getJdbcUrl();
        String testDbUrl;
        if (masterUrl.contains("databaseName=")) {
            testDbUrl = masterUrl.replaceAll("databaseName=[^;]+", "databaseName=" + testDbName);
        } else {
            testDbUrl = masterUrl + ";databaseName=" + testDbName;
        }

        DriverManagerDataSource testDs = new DriverManagerDataSource();
        testDs.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        testDs.setUrl(testDbUrl);
        testDs.setUsername(MSSQL_SERVER_CONTAINER.getUsername());
        testDs.setPassword(MSSQL_SERVER_CONTAINER.getPassword());

        JdbcTemplate testJdbc = new JdbcTemplate(testDs);

        try {
            // 1. Programmatically migrate to target V21 using configured flyway locations
            Flyway flywayV21 = Flyway.configure()
                    .dataSource(testDs)
                    .locations(getFlywayLocationArray())
                    .target("21")
                    .load();
            flywayV21.migrate();

            // Insert a test user and member profile in the V21 database
            testJdbc.update("INSERT INTO [user_account] (email, password_hash, full_name, role_id, status, created_at) " +
                    "VALUES ('mig_test_user@test.com', 'hash', 'Mig Test', (SELECT TOP 1 id FROM [role] WHERE code = 'MEMBER'), 'ACTIVE', SYSDATETIME())");
            Long migMemberId = testJdbc.queryForObject("SELECT id FROM [user_account] WHERE email = 'mig_test_user@test.com'", Long.class);
            testJdbc.update("INSERT INTO [member_profile] (user_id, member_code, current_level, joined_on) VALUES (?, 'MBR-MIG-01', 'BEGINNER', CAST(SYSDATETIME() AS DATE))", migMemberId);

            // Insert dedicated test tiers into the V21 database with fixed explicit prices and unique codes
            testJdbc.update("INSERT INTO [membership_card_tier] (code, name, price, duration_months, discount_percentage, is_active, created_at) " +
                    "VALUES ('TIER-MIG-1', 'Mig Tier 1', 200000.00, 12, 5, 1, SYSDATETIME())");
            Long testTier1Id = testJdbc.queryForObject("SELECT id FROM [membership_card_tier] WHERE code = 'TIER-MIG-1'", Long.class);

            testJdbc.update("INSERT INTO [membership_card_tier] (code, name, price, duration_months, discount_percentage, is_active, created_at) " +
                    "VALUES ('TIER-MIG-2', 'Mig Tier 2', 500000.00, 12, 10, 1, SYSDATETIME())");
            Long testTier2Id = testJdbc.queryForObject("SELECT id FROM [membership_card_tier] WHERE code = 'TIER-MIG-2'", Long.class);

            BigDecimal tier1Price = BigDecimal.valueOf(200000.00);
            BigDecimal tier2Price = BigDecimal.valueOf(500000.00);
            BigDecimal upgDiff = tier2Price.subtract(tier1Price); // 300,000.00

            // 2. Seed legacy card rows in V21 schema across four test cases:
            // Case 1: Clean chain A -> B -> C (A is replaced by B, B is replaced by C, all sharing identical end_date)
            LocalDate endDec = LocalDate.of(2026, 12, 31);
            testJdbc.update("INSERT INTO [member_card] (card_code, member_id, tier_id, start_date, end_date, status, price_paid, created_at, updated_at) " +
                    "VALUES ('CARD-MIG-A', ?, ?, '2026-01-01', ?, 'REPLACED', ?, GETDATE(), GETDATE())", migMemberId, testTier1Id, endDec, tier1Price);
            Long cardAId = testJdbc.queryForObject("SELECT id FROM [member_card] WHERE card_code = 'CARD-MIG-A'", Long.class);

            testJdbc.update("INSERT INTO [member_card] (card_code, member_id, tier_id, start_date, end_date, status, price_paid, created_at, updated_at) " +
                    "VALUES ('CARD-MIG-B', ?, ?, '2026-03-01', ?, 'REPLACED', ?, GETDATE(), GETDATE())", migMemberId, testTier2Id, endDec, upgDiff.divide(BigDecimal.valueOf(2)));
            Long cardBId = testJdbc.queryForObject("SELECT id FROM [member_card] WHERE card_code = 'CARD-MIG-B'", Long.class);

            testJdbc.update("INSERT INTO [member_card] (card_code, member_id, tier_id, start_date, end_date, status, price_paid, created_at, updated_at) " +
                    "VALUES ('CARD-MIG-C', ?, ?, '2026-06-01', ?, 'ACTIVE', ?, GETDATE(), GETDATE())", migMemberId, testTier2Id, endDec, upgDiff.divide(BigDecimal.valueOf(2)));
            Long cardCId = testJdbc.queryForObject("SELECT id FROM [member_card] WHERE card_code = 'CARD-MIG-C'", Long.class);

            // Case 2: No predecessor (card with price_paid < tier price, but no candidate predecessor exists sharing end_date)
            LocalDate endNov = LocalDate.of(2026, 11, 30);
            testJdbc.update("INSERT INTO [member_card] (card_code, member_id, tier_id, start_date, end_date, status, price_paid, created_at, updated_at) " +
                    "VALUES ('CARD-MIG-NOPRED', ?, ?, '2026-05-01', ?, 'ACTIVE', ?, GETDATE(), GETDATE())", migMemberId, testTier2Id, endNov, upgDiff);
            Long noPredCardId = testJdbc.queryForObject("SELECT id FROM [member_card] WHERE card_code = 'CARD-MIG-NOPRED'", Long.class);

            // Case 3: Multiple predecessors (ambiguous candidate where multiple REPLACED cards share end_date)
            LocalDate endOct = LocalDate.of(2026, 10, 31);
            testJdbc.update("INSERT INTO [member_card] (card_code, member_id, tier_id, start_date, end_date, status, price_paid, created_at, updated_at) " +
                    "VALUES ('CARD-MIG-AMBIG-P1', ?, ?, '2026-01-01', ?, 'REPLACED', ?, GETDATE(), GETDATE())", migMemberId, testTier1Id, endOct, tier1Price);
            testJdbc.update("INSERT INTO [member_card] (card_code, member_id, tier_id, start_date, end_date, status, price_paid, created_at, updated_at) " +
                    "VALUES ('CARD-MIG-AMBIG-P2', ?, ?, '2026-02-01', ?, 'REPLACED', ?, GETDATE(), GETDATE())", migMemberId, testTier1Id, endOct, tier1Price);
            testJdbc.update("INSERT INTO [member_card] (card_code, member_id, tier_id, start_date, end_date, status, price_paid, created_at, updated_at) " +
                    "VALUES ('CARD-MIG-AMBIG-CAND', ?, ?, '2026-04-01', ?, 'ACTIVE', ?, GETDATE(), GETDATE())", migMemberId, testTier2Id, endOct, upgDiff);
            Long ambigCandCardId = testJdbc.queryForObject("SELECT id FROM [member_card] WHERE card_code = 'CARD-MIG-AMBIG-CAND'", Long.class);

            // Case 4: Duplicate in-force cards sharing the same candidate predecessor (Step 5 fails closed: both end up NULL)
            LocalDate endSep = LocalDate.of(2026, 9, 30);
            testJdbc.update("INSERT INTO [member_card] (card_code, member_id, tier_id, start_date, end_date, status, price_paid, created_at, updated_at) " +
                    "VALUES ('CARD-MIG-DUP-BASE', ?, ?, '2026-01-01', ?, 'REPLACED', ?, GETDATE(), GETDATE())", migMemberId, testTier1Id, endSep, tier1Price);
            Long dupBaseCardId = testJdbc.queryForObject("SELECT id FROM [member_card] WHERE card_code = 'CARD-MIG-DUP-BASE'", Long.class);

            testJdbc.update("INSERT INTO [member_card] (card_code, member_id, tier_id, start_date, end_date, status, price_paid, created_at, updated_at) " +
                    "VALUES ('CARD-MIG-DUP-1', ?, ?, '2026-03-01', ?, 'ACTIVE', ?, GETDATE(), GETDATE())", migMemberId, testTier2Id, endSep, upgDiff.divide(BigDecimal.valueOf(2)));
            Long cardD1Id = testJdbc.queryForObject("SELECT id FROM [member_card] WHERE card_code = 'CARD-MIG-DUP-1'", Long.class);

            testJdbc.update("INSERT INTO [member_card] (card_code, member_id, tier_id, start_date, end_date, status, price_paid, created_at, updated_at) " +
                    "VALUES ('CARD-MIG-DUP-2', ?, ?, '2026-04-01', ?, 'ACTIVE', ?, GETDATE(), GETDATE())", migMemberId, testTier2Id, endSep, upgDiff.divide(BigDecimal.valueOf(2)));
            Long cardD2Id = testJdbc.queryForObject("SELECT id FROM [member_card] WHERE card_code = 'CARD-MIG-DUP-2'", Long.class);

            // 3. Migrate to V22
            Flyway flywayV22 = Flyway.configure()
                    .dataSource(testDs)
                    .locations(getFlywayLocationArray())
                    .target("22")
                    .load();
            flywayV22.migrate();

            // 4. Assert backfill results
            // Case 1: Clean chain A -> B -> C
            Boolean upgA = testJdbc.queryForObject("SELECT upgrade_request FROM [member_card] WHERE id = ?", Boolean.class, cardAId);
            Long repA = testJdbc.queryForObject("SELECT replaces_card_id FROM [member_card] WHERE id = ?", Long.class, cardAId);
            assertFalse(Boolean.TRUE.equals(upgA), "Initial card A was not purchased as upgrade difference");
            assertNull(repA, "Initial card A does not replace any card");

            Boolean upgB = testJdbc.queryForObject("SELECT upgrade_request FROM [member_card] WHERE id = ?", Boolean.class, cardBId);
            Long repB = testJdbc.queryForObject("SELECT replaces_card_id FROM [member_card] WHERE id = ?", Long.class, cardBId);
            assertTrue(Boolean.TRUE.equals(upgB), "Card B paid < tier price, must be marked as upgrade");
            assertEquals(cardAId, repB, "Card B must be linked to predecessor Card A");

            Boolean upgC = testJdbc.queryForObject("SELECT upgrade_request FROM [member_card] WHERE id = ?", Boolean.class, cardCId);
            Long repC = testJdbc.queryForObject("SELECT replaces_card_id FROM [member_card] WHERE id = ?", Long.class, cardCId);
            assertTrue(Boolean.TRUE.equals(upgC), "Card C paid < tier price, must be marked as upgrade");
            assertEquals(cardBId, repC, "Card C must be linked to immediate predecessor Card B");

            // Case 2: No predecessor -> upgrade_request = 1, replaces_card_id = NULL
            Boolean upgNoPred = testJdbc.queryForObject("SELECT upgrade_request FROM [member_card] WHERE id = ?", Boolean.class, noPredCardId);
            Long repNoPred = testJdbc.queryForObject("SELECT replaces_card_id FROM [member_card] WHERE id = ?", Long.class, noPredCardId);
            assertTrue(Boolean.TRUE.equals(upgNoPred), "No-predecessor card marked as upgrade by price difference");
            assertNull(repNoPred, "Card without matching predecessor must keep replaces_card_id as NULL");

            // Case 3: Ambiguous predecessors -> upgrade_request = 1, replaces_card_id = NULL
            Boolean upgAmbig = testJdbc.queryForObject("SELECT upgrade_request FROM [member_card] WHERE id = ?", Boolean.class, ambigCandCardId);
            Long repAmbig = testJdbc.queryForObject("SELECT replaces_card_id FROM [member_card] WHERE id = ?", Long.class, ambigCandCardId);
            assertTrue(Boolean.TRUE.equals(upgAmbig), "Ambiguous candidate card marked as upgrade by price difference");
            assertNull(repAmbig, "Ambiguous candidate card with multiple predecessors must keep replaces_card_id as NULL");

            // Case 4: Duplicate in-force cards sharing the same candidate predecessor (Step 5 fails closed: both end up NULL)
            Boolean upgD1 = testJdbc.queryForObject("SELECT upgrade_request FROM [member_card] WHERE id = ?", Boolean.class, cardD1Id);
            Long repD1 = testJdbc.queryForObject("SELECT replaces_card_id FROM [member_card] WHERE id = ?", Long.class, cardD1Id);
            assertTrue(Boolean.TRUE.equals(upgD1), "Card D1 marked as upgrade");
            assertNull(repD1, "Card D1 in duplicate in-force group must have replaces_card_id set to NULL");

            Boolean upgD2 = testJdbc.queryForObject("SELECT upgrade_request FROM [member_card] WHERE id = ?", Boolean.class, cardD2Id);
            Long repD2 = testJdbc.queryForObject("SELECT replaces_card_id FROM [member_card] WHERE id = ?", Long.class, cardD2Id);
            assertTrue(Boolean.TRUE.equals(upgD2), "Card D2 marked as upgrade");
            assertNull(repD2, "Card D2 in duplicate in-force group must have replaces_card_id set to NULL");

            // 5. Snapshot before re-migration
            List<Map<String, Object>> snapshotBefore = testJdbc.queryForList(
                    "SELECT id, card_code, upgrade_request, replaces_card_id, price_paid, status FROM [member_card] ORDER BY id");

            // Verify idempotency: delete V22 entry from flyway_schema_history and re-migrate to V22
            testJdbc.update("DELETE FROM [flyway_schema_history] WHERE [version] = '22'");
            flywayV22.migrate();

            List<Map<String, Object>> snapshotAfter = testJdbc.queryForList(
                    "SELECT id, card_code, upgrade_request, replaces_card_id, price_paid, status FROM [member_card] ORDER BY id");
            assertEquals(snapshotBefore, snapshotAfter, "Member card snapshot must be identical after re-running V22");

            // Assert constraints and unique indices exist exactly once
            int indexCount = testJdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys.indexes WHERE name = 'ux_member_card_replaces_in_force'", Integer.class);
            assertEquals(1, indexCount, "Index ux_member_card_replaces_in_force must exist exactly once");

            int tierRankIndexCount = testJdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys.indexes WHERE name = 'ux_membership_card_tier_rank'", Integer.class);
            assertEquals(1, tierRankIndexCount, "Index ux_membership_card_tier_rank must exist exactly once");

            int tierRankChkCount = testJdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys.check_constraints WHERE name = 'chk_membership_card_tier_rank'", Integer.class);
            assertEquals(1, tierRankChkCount, "Check constraint chk_membership_card_tier_rank must exist exactly once");
        } finally {
            try {
                jdbcTemplate.execute("ALTER DATABASE [" + testDbName + "] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [" + testDbName + "];");
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @DisplayName("Legacy upgrade card with unresolved replaces_card_id fails closed on upgrade attempt")
    void testUpgrade_legacyUnresolvedCard_failsClosed() {
        MembershipCardTier goldTier = cardTierRepository.findByCode("GOLD").orElseThrow();
        MembershipCardTier vipTier = cardTierRepository.findByCode("VIP").orElseThrow();

        // Create an active card marked as upgrade but with NULL replaces_card_id
        MemberCard legacyCard = memberCardRepository.save(MemberCard.builder()
                .cardCode("CARD-UNRES-" + MARKER)
                .member(memberProfile)
                .tier(goldTier)
                .startDate(LocalDate.now().minusMonths(1))
                .endDate(LocalDate.now().plusMonths(11))
                .status(CardStatus.ACTIVE)
                .pricePaid(BigDecimal.valueOf(150000.00))
                .upgradeRequest(true)
                .replacesCard(null)
                .build());

        MemberCardPurchaseRequest upgradeReq = MemberCardPurchaseRequest.builder()
                .memberId(memberProfile.getId())
                .tierId(vipTier.getId())
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                membershipCardService.purchaseCard(upgradeReq, managerUser)
        );
        assertTrue(ex.getMessage().contains("Cannot determine upgrade baseline"),
                "Unresolved legacy upgrade must fail closed and direct member to front desk: " + ex.getMessage());
    }

    @Test
    @DisplayName("Unique filtered index ux_member_card_replaces_in_force rejects concurrent double replacement of in-force card")
    void testConcurrency_filteredIndexRejectsDuplicateReplacesCardId() {
        MembershipCardTier goldTier = cardTierRepository.findByCode("GOLD").orElseThrow();
        MembershipCardTier vipTier = cardTierRepository.findByCode("VIP").orElseThrow();

        MemberCard baseCard = memberCardRepository.save(MemberCard.builder()
                .cardCode("CARD-BASE-" + MARKER)
                .member(memberProfile)
                .tier(goldTier)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(12))
                .status(CardStatus.ACTIVE)
                .pricePaid(goldTier.getPrice())
                .build());

        // First in-force upgrade targeting baseCard succeeds
        memberCardRepository.saveAndFlush(MemberCard.builder()
                .cardCode("CARD-UPG1-" + MARKER)
                .member(memberProfile)
                .tier(vipTier)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(12))
                .status(CardStatus.PENDING_PAYMENT)
                .pricePaid(BigDecimal.valueOf(300000.00))
                .upgradeRequest(true)
                .replacesCard(baseCard)
                .build());

        // Second in-force upgrade concurrently attempting to replace the same baseCard must fail due to filtered index
        MemberCard duplicateUpgrade = MemberCard.builder()
                .cardCode("CARD-UPG2-" + MARKER)
                .member(memberProfile)
                .tier(vipTier)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(12))
                .status(CardStatus.PENDING_PAYMENT)
                .pricePaid(BigDecimal.valueOf(300000.00))
                .upgradeRequest(true)
                .replacesCard(baseCard)
                .build();

        assertThrows(DataAccessException.class, () -> {
            memberCardRepository.saveAndFlush(duplicateUpgrade);
        }, "Unique filtered index must block duplicate in-force cards replacing the same base card");
    }

    @Test
    @DisplayName("Two concurrent upgrade requests targeting the same active card: exactly one succeeds and one fails gracefully across 5 iterations")
    void testConcurrency_duplicateUpgradeFailsGracefully() throws Exception {
        MembershipCardTier goldTier = cardTierRepository.findByCode("GOLD").orElseThrow();
        MembershipCardTier vipTier = cardTierRepository.findByCode("VIP").orElseThrow();

        for (int iter = 0; iter < 5; iter++) {
            // Clean up member cards and payments before each iteration
            jdbcTemplate.update("UPDATE member_card SET replaces_card_id = NULL WHERE member_id = ?", memberProfile.getId());
            jdbcTemplate.update("UPDATE member_card SET payment_id = NULL WHERE member_id = ?", memberProfile.getId());
            jdbcTemplate.update("DELETE FROM member_card WHERE member_id = ?", memberProfile.getId());
            jdbcTemplate.update("DELETE FROM payment WHERE member_id = ?", memberProfile.getId());

            MemberCard baseCard = memberCardRepository.saveAndFlush(MemberCard.builder()
                    .cardCode("CARD-CONC-" + iter + "-" + MARKER)
                    .member(memberProfile)
                    .tier(goldTier)
                    .startDate(LocalDate.now())
                    .endDate(LocalDate.now().plusMonths(12))
                    .status(CardStatus.ACTIVE)
                    .pricePaid(goldTier.getPrice())
                    .build());

            MemberCardPurchaseRequest upgradeReq = MemberCardPurchaseRequest.builder()
                    .memberId(memberProfile.getId())
                    .tierId(vipTier.getId())
                    .paymentMethod(PaymentMethod.BANK_TRANSFER)
                    .build();

            int threadCount = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CyclicBarrier barrier = new CyclicBarrier(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger businessRuleExCount = new AtomicInteger(0);
            List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        barrier.await();
                        membershipCardService.purchaseCard(upgradeReq, memberUser);
                        successCount.incrementAndGet();
                    } catch (BusinessRuleException e) {
                        businessRuleExCount.incrementAndGet();
                    } catch (Throwable t) {
                        unexpectedErrors.add(t);
                    }
                });
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

            assertEquals(0, unexpectedErrors.size(), "Iteration " + iter + " had unexpected errors: " + unexpectedErrors);
            assertEquals(1, successCount.get(), "Iteration " + iter + ": exactly one concurrent upgrade should succeed");
            assertEquals(1, businessRuleExCount.get(), "Iteration " + iter + ": exactly one concurrent upgrade should receive BusinessRuleException");

            Integer inForceCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM member_card WHERE replaces_card_id = ? AND status IN ('ACTIVE', 'PENDING_PAYMENT')",
                    Integer.class,
                    baseCard.getId());
            assertEquals(1, inForceCount, "Iteration " + iter + ": exactly one in-force card must replace baseCard in database");

            // Clean up iteration cards and payments
            jdbcTemplate.update("UPDATE member_card SET replaces_card_id = NULL WHERE member_id = ?", memberProfile.getId());
            jdbcTemplate.update("UPDATE member_card SET payment_id = NULL WHERE member_id = ?", memberProfile.getId());
            jdbcTemplate.update("DELETE FROM member_card WHERE member_id = ?", memberProfile.getId());
            jdbcTemplate.update("DELETE FROM payment WHERE member_id = ?", memberProfile.getId());
        }
    }

    @Test
    @DisplayName("Updating STANDARD tier price != 0 via HTTP returns 400 Bad Request")
    void testUpdateTier_standardPriceChange_returns400() throws Exception {
        MembershipCardTier standardTier = cardTierRepository.findByCode("STANDARD").orElseThrow();
        MembershipCardTierUpdateRequest req = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.valueOf(100000.00))
                .durationMonths(0)
                .discountPercentage(0)
                .active(true)
                .build();

        mockMvc.perform(put("/api/v1/membership-cards/tiers/" + standardTier.getId())
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("The STANDARD tier price cannot be changed")));
    }

    @Test
    @DisplayName("Deactivating STANDARD tier via HTTP returns 400 Bad Request")
    void testUpdateTier_standardDeactivate_returns400() throws Exception {
        MembershipCardTier standardTier = cardTierRepository.findByCode("STANDARD").orElseThrow();
        MembershipCardTierUpdateRequest req = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.ZERO)
                .durationMonths(0)
                .discountPercentage(0)
                .active(false)
                .build();

        mockMvc.perform(put("/api/v1/membership-cards/tiers/" + standardTier.getId())
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("The STANDARD tier cannot be deactivated")));
    }

    @Test
    @DisplayName("Updating paid tier price to 0 via HTTP returns 400 Bad Request")
    void testUpdateTier_paidTierPriceZero_returns400() throws Exception {
        MembershipCardTier goldTier = cardTierRepository.findByCode("GOLD").orElseThrow();
        MembershipCardTierUpdateRequest req = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.ZERO)
                .durationMonths(12)
                .discountPercentage(5)
                .active(true)
                .build();

        mockMvc.perform(put("/api/v1/membership-cards/tiers/" + goldTier.getId())
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Price must be greater than zero for paid tiers")));
    }

    @Test
    @DisplayName("Updating sport package price to 0 via HTTP returns 400 Bad Request")
    void testUpdatePackage_priceZero_returns400() throws Exception {
        SportPackage pkg = sportPackageRepository.save(SportPackage.builder()
                .code("PKG-ZERO-" + MARKER)
                .name("Zero Price Pkg " + MARKER)
                .sport(activeSport1)
                .trainingFormat(TrainingFormat.SELF_TRAINING)
                .durationDays(30)
                .sessionCount(10)
                .sessionMinutes(60)
                .priceAmount(BigDecimal.valueOf(150000.00))
                .isActive(true)
                .build());

        SportPackageUpdateRequest req = SportPackageUpdateRequest.builder()
                .name("Zero Price Pkg " + MARKER)
                .price(BigDecimal.ZERO)
                .sessionCount(10)
                .durationDays(30)
                .sessionMinutes(60)
                .active(true)
                .build();

        mockMvc.perform(put("/api/v1/packages/" + pkg.getId())
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Updating sport package session minutes to 45 via HTTP returns 400 Bad Request")
    void testUpdatePackage_sessionMinutes45_returns400() throws Exception {
        SportPackage pkg = sportPackageRepository.save(SportPackage.builder()
                .code("PKG-MIN45-" + MARKER)
                .name("Min 45 Pkg " + MARKER)
                .sport(activeSport1)
                .trainingFormat(TrainingFormat.SELF_TRAINING)
                .durationDays(30)
                .sessionCount(10)
                .sessionMinutes(60)
                .priceAmount(BigDecimal.valueOf(150000.00))
                .isActive(true)
                .build());

        SportPackageUpdateRequest req = SportPackageUpdateRequest.builder()
                .name("Min 45 Pkg " + MARKER)
                .price(BigDecimal.valueOf(150000.00))
                .sessionCount(10)
                .durationDays(30)
                .sessionMinutes(45)
                .active(true)
                .build();

        mockMvc.perform(put("/api/v1/packages/" + pkg.getId())
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Session minutes must be either 60 or 90")));
    }

    @Test
    @DisplayName("Member purchase upgrade for legacy card with null replaces_card_id returns 400 Bad Request via HTTP")
    void testPurchaseUpgrade_legacyUnresolvedCard_returns400() throws Exception {
        MembershipCardTier goldTier = cardTierRepository.findByCode("GOLD").orElseThrow();
        MembershipCardTier vipTier = cardTierRepository.findByCode("VIP").orElseThrow();

        // Create active legacy card with upgradeRequest = true and replacesCard = null
        memberCardRepository.saveAndFlush(MemberCard.builder()
                .cardCode("CARD-MOCK-UNRES-" + MARKER)
                .member(memberProfile)
                .tier(goldTier)
                .startDate(LocalDate.now().minusMonths(1))
                .endDate(LocalDate.now().plusMonths(11))
                .status(CardStatus.ACTIVE)
                .pricePaid(BigDecimal.valueOf(150000.00))
                .upgradeRequest(true)
                .replacesCard(null)
                .build());

        MemberCardPurchaseRequest upgradeReq = MemberCardPurchaseRequest.builder()
                .memberId(memberProfile.getId())
                .tierId(vipTier.getId())
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .build();

        String memberToken = jwtService.generateToken(new CustomUserDetails(memberUser));

        mockMvc.perform(post("/api/v1/membership-cards/purchase")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upgradeReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Cannot determine upgrade baseline")));
    }

    @Test
    @DisplayName("Updating card tier durationMonths to 121 returns 400; durationMonths 120 returns 200")
    void testUpdateTier_durationMonthsUpperBounds() throws Exception {
        MembershipCardTier goldTier = cardTierRepository.findByCode("GOLD").orElseThrow();

        // 121 months -> 400 Bad Request
        MembershipCardTierUpdateRequest invalidReq = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.valueOf(500000.00))
                .durationMonths(121)
                .discountPercentage(5)
                .active(true)
                .build();

        mockMvc.perform(put("/api/v1/membership-cards/tiers/" + goldTier.getId())
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 120 months -> 200 OK
        MembershipCardTierUpdateRequest validReq = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.valueOf(500000.00))
                .durationMonths(120)
                .discountPercentage(5)
                .active(true)
                .build();

        mockMvc.perform(put("/api/v1/membership-cards/tiers/" + goldTier.getId())
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMonths").value(120));
    }

    @Test
    @DisplayName("Updating sport package sessionCount to 1001 returns 400; sessionCount 1000 returns 200")
    void testUpdatePackage_sessionCountUpperBounds() throws Exception {
        SportPackage pkg = sportPackageRepository.save(SportPackage.builder()
                .code("PKG-SESS-UB-" + MARKER)
                .name("Session UB Pkg " + MARKER)
                .sport(activeSport1)
                .trainingFormat(TrainingFormat.SELF_TRAINING)
                .durationDays(30)
                .sessionCount(10)
                .sessionMinutes(60)
                .priceAmount(BigDecimal.valueOf(150000.00))
                .isActive(true)
                .build());

        // 1001 sessions -> 400 Bad Request
        SportPackageUpdateRequest invalidReq = SportPackageUpdateRequest.builder()
                .name("Session UB Pkg " + MARKER)
                .price(BigDecimal.valueOf(150000.00))
                .sessionCount(1001)
                .durationDays(30)
                .sessionMinutes(60)
                .active(true)
                .build();

        mockMvc.perform(put("/api/v1/packages/" + pkg.getId())
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 1000 sessions -> 200 OK
        SportPackageUpdateRequest validReq = SportPackageUpdateRequest.builder()
                .name("Session UB Pkg " + MARKER)
                .price(BigDecimal.valueOf(150000.00))
                .sessionCount(1000)
                .durationDays(30)
                .sessionMinutes(60)
                .active(true)
                .build();

        mockMvc.perform(put("/api/v1/packages/" + pkg.getId())
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCount").value(1000));
    }

    @Test
    @DisplayName("Updating sport package durationDays to 3651 returns 400; durationDays 3650 returns 200")
    void testUpdatePackage_durationDaysUpperBounds() throws Exception {
        SportPackage pkg = sportPackageRepository.save(SportPackage.builder()
                .code("PKG-DAYS-UB-" + MARKER)
                .name("Days UB Pkg " + MARKER)
                .sport(activeSport1)
                .trainingFormat(TrainingFormat.SELF_TRAINING)
                .durationDays(30)
                .sessionCount(10)
                .sessionMinutes(60)
                .priceAmount(BigDecimal.valueOf(150000.00))
                .isActive(true)
                .build());

        // 3651 days -> 400 Bad Request
        SportPackageUpdateRequest invalidReq = SportPackageUpdateRequest.builder()
                .name("Days UB Pkg " + MARKER)
                .price(BigDecimal.valueOf(150000.00))
                .sessionCount(10)
                .durationDays(3651)
                .sessionMinutes(60)
                .active(true)
                .build();

        mockMvc.perform(put("/api/v1/packages/" + pkg.getId())
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 3650 days -> 200 OK
        SportPackageUpdateRequest validReq = SportPackageUpdateRequest.builder()
                .name("Days UB Pkg " + MARKER)
                .price(BigDecimal.valueOf(150000.00))
                .sessionCount(10)
                .durationDays(3650)
                .sessionMinutes(60)
                .active(true)
                .build();

        mockMvc.perform(put("/api/v1/packages/" + pkg.getId())
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationDays").value(3650));
    }

    @Test
    @DisplayName("Activating legacy upgrade card with null replaces_card_id fails closed directing to front desk")
    void testActivateCard_legacyUnresolvedUpgrade_failsClosed() {
        MembershipCardTier vipTier = cardTierRepository.findByCode("VIP").orElseThrow();

        // Pending upgrade card with replacesCard = null
        MemberCard legacyPendingCard = memberCardRepository.save(MemberCard.builder()
                .cardCode("CARD-ACT-UNRES-" + MARKER)
                .member(memberProfile)
                .tier(vipTier)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(12))
                .status(CardStatus.PENDING_PAYMENT)
                .pricePaid(BigDecimal.valueOf(300000.00))
                .upgradeRequest(true)
                .replacesCard(null)
                .build());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                membershipCardService.activateCard(legacyPendingCard.getId(), managerUser)
        );
        assertTrue(ex.getMessage().contains("Cannot determine upgrade baseline"),
                "Activating legacy upgrade with null replaces_card_id must fail closed: " + ex.getMessage());
    }
}
