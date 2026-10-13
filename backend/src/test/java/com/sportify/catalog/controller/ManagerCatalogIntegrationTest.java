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
import com.sportify.payment.entity.PaymentMethod;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
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
import com.sportify.identity.entity.ActivityLog;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.ActivityLogRepository;
import com.sportify.identity.repository.MemberProfileRepository;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.sportify.core.exception.BusinessRuleException;
import org.springframework.dao.DataAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Tag;

/**
 * Integration tests for task BE-03 Manager Catalog and Pricing features.
 * Extends AbstractIntegrationTest (uses Testcontainers; skipped locally when Docker is unavailable).
 */
@Tag("integration")
class ManagerCatalogIntegrationTest extends AbstractIntegrationTest {

    private static final String MARKER = "be03it";

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
        MembershipCardTier goldTier = cardTierRepository.findByCode("GOLD")
                .orElseThrow(() -> new IllegalStateException("Seeded GOLD tier required"));
        BigDecimal originalPrice = goldTier.getPrice();
        Integer originalDiscount = goldTier.getDiscountPercentage();

        MembershipCardTierUpdateRequest request = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.valueOf(380000.00))
                .durationMonths(12)
                .discountPercentage(7)
                .description("Updated gold tier description " + MARKER)
                .active(true)
                .build();

        membershipCardService.updateTier(goldTier.getId(), request, managerUser);

        // Verify entity persisted in DB
        MembershipCardTier reloaded = cardTierRepository.findById(goldTier.getId()).orElseThrow();
        assertEquals(goldTier.getName(), reloaded.getName());
        assertEquals(0, BigDecimal.valueOf(380000.00).compareTo(reloaded.getPrice()));
        assertEquals(7, reloaded.getDiscountPercentage());
        assertEquals("Updated gold tier description " + MARKER, reloaded.getDescription());

        // Verify activity_log row written with before and after snapshots
        List<ActivityLog> logs = activityLogRepository.findAll().stream()
                .filter(l -> "MEMBERSHIP_CARD_TIER".equals(l.getEntityType()) && goldTier.getId().equals(l.getEntityId()))
                .filter(l -> AuditAction.CARD_TIER_UPDATED.equals(l.getAction()))
                .toList();
        assertFalse(logs.isEmpty(), "Activity log row must be written for tier update");
        ActivityLog log = logs.get(logs.size() - 1);
        assertEquals(managerUser.getId(), log.getActor().getId());

        JsonNode detailsNode = objectMapper.readTree(log.getDetails());
        assertTrue(detailsNode.has("before"));
        assertTrue(detailsNode.has("after"));
        assertEquals(originalPrice.doubleValue(), detailsNode.get("before").get("price").asDouble());
        assertEquals(380000.00, detailsNode.get("after").get("price").asDouble());
        assertEquals(originalDiscount.intValue(), detailsNode.get("before").get("discountPercentage").asInt());
        assertEquals(7, detailsNode.get("after").get("discountPercentage").asInt());

        // Restore tier price/discount for clean state
        goldTier.setPrice(originalPrice);
        goldTier.setDiscountPercentage(originalDiscount);
        goldTier.setName("Gold");
        cardTierRepository.saveAndFlush(goldTier);
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

        // 2. Create an active member card for gold tier
        MembershipCardTier goldTier = cardTierRepository.findByCode("GOLD")
                .orElseThrow(() -> new IllegalStateException("Seeded GOLD tier required"));
        BigDecimal originalTierPrice = goldTier.getPrice();

        MemberCard card = memberCardRepository.save(MemberCard.builder()
                .cardCode("CARD-" + MARKER)
                .member(memberProfile)
                .tier(goldTier)
                .pricePaid(BigDecimal.valueOf(300000.00))
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

        // 4. Manager updates tier price to 450,000
        MembershipCardTierUpdateRequest tierUpdateReq = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.valueOf(450000.00))
                .durationMonths(goldTier.getDurationMonths())
                .discountPercentage(goldTier.getDiscountPercentage())
                .active(true)
                .build();
        membershipCardService.updateTier(goldTier.getId(), tierUpdateReq, managerUser);

        // 5. Assert existing registration still retains snapshot price in DB
        SportPackageRegistration reloadedReg = registrationRepository.findById(registration.getId()).orElseThrow();
        assertEquals(0, BigDecimal.valueOf(500000.00).compareTo(reloadedReg.getOriginalPrice()),
                "Historical originalPrice must remain 500000");
        assertEquals(0, BigDecimal.valueOf(500000.00).compareTo(reloadedReg.getPaidAmount()),
                "Historical paidAmount must remain 500000");
        assertEquals(0, reloadedReg.getDiscountPercentage());

        // 6. Assert existing member card still retains snapshot pricePaid in DB
        MemberCard reloadedCard = memberCardRepository.findById(card.getId()).orElseThrow();
        assertEquals(0, BigDecimal.valueOf(300000.00).compareTo(reloadedCard.getPricePaid()),
                "Historical pricePaid must remain 300000");

        // Restore tier price
        goldTier.setPrice(originalTierPrice);
        cardTierRepository.saveAndFlush(goldTier);
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
        MembershipCardTier goldTier = cardTierRepository.findByCode("GOLD")
                .orElseThrow(() -> new IllegalStateException("Seeded GOLD tier required"));
        BigDecimal originalPrice = goldTier.getPrice();

        // 1. Member creates pending card at original price
        MemberCardPurchaseRequest purchaseReq = MemberCardPurchaseRequest.builder()
                .memberId(memberProfile.getId())
                .tierId(goldTier.getId())
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
                .durationMonths(goldTier.getDurationMonths())
                .discountPercentage(goldTier.getDiscountPercentage())
                .active(true)
                .build();
        membershipCardService.updateTier(goldTier.getId(), tierUpdateReq, managerUser);

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

        // Restore tier price
        goldTier.setPrice(originalPrice);
        cardTierRepository.saveAndFlush(goldTier);
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
                .tierRank(1)
                .isActive(true)
                .build());

        MembershipCardTier tierB = cardTierRepository.save(MembershipCardTier.builder()
                .code("TIER-B-" + MARKER)
                .name("Tier B " + MARKER)
                .price(BigDecimal.valueOf(350000.00))
                .durationMonths(12)
                .discountPercentage(5)
                .tierRank(2)
                .isActive(true)
                .build());

        MembershipCardTier tierC = cardTierRepository.save(MembershipCardTier.builder()
                .code("TIER-C-" + MARKER)
                .name("Tier C " + MARKER)
                .price(BigDecimal.valueOf(650000.00))
                .durationMonths(12)
                .discountPercentage(10)
                .tierRank(3)
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
                .price(BigDecimal.valueOf(300000.00))
                .durationMonths(12)
                .discountPercentage(5)
                .tierRank(1)
                .isActive(true)
                .build());

        MembershipCardTier goldTier = cardTierRepository.save(MembershipCardTier.builder()
                .code("GOLD-" + MARKER)
                .name("Gold " + MARKER)
                .price(BigDecimal.valueOf(600000.00))
                .durationMonths(12)
                .discountPercentage(10)
                .tierRank(2)
                .isActive(true)
                .build());

        // 1. Member purchases Silver card at 300k
        MemberCardPurchaseRequest silverReq = MemberCardPurchaseRequest.builder()
                .memberId(memberProfile.getId())
                .tierId(silverTier.getId())
                .build();
        MemberCardResponse silverCard = membershipCardService.purchaseCard(silverReq, managerUser);
        assertEquals(CardStatus.ACTIVE, silverCard.getStatus());
        assertEquals(0, BigDecimal.valueOf(300000.00).compareTo(silverCard.getPricePaid()));

        // 2. Manager raises Silver tier price to 450,000
        MembershipCardTierUpdateRequest silverUpdate = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.valueOf(450000.00))
                .durationMonths(12)
                .discountPercentage(5)
                .active(true)
                .build();
        membershipCardService.updateTier(silverTier.getId(), silverUpdate, managerUser);

        // 3. Member requests upgrade to Gold (600,000)
        MemberCardPurchaseRequest goldReq = MemberCardPurchaseRequest.builder()
                .memberId(memberProfile.getId())
                .tierId(goldTier.getId())
                .build();
        MemberCardResponse goldCard = membershipCardService.purchaseCard(goldReq, managerUser);

        // Upgrade fee must be 600k - 300k (pricePaid) = 300k, NOT 600k - 450k (live) = 150k
        assertEquals(0, BigDecimal.valueOf(300000.00).compareTo(goldCard.getPricePaid()),
                "Upgrade fee must be target tier price (600k) minus actual pricePaid (300k) = 300k");
    }

    @Test
    @DisplayName("After manager edit of paid tier, member purchase produces PENDING_PAYMENT card requiring payment (R-01 regression)")
    void testPurchaseAfterManagerEditOfPaidTier_producesPendingPaymentCardRequiringPayment() {
        MembershipCardTier goldTier = cardTierRepository.findByCode("GOLD")
                .orElseThrow(() -> new IllegalStateException("Seeded GOLD tier required"));
        BigDecimal originalPrice = goldTier.getPrice();

        // 1. Manager edits tier price and duration
        MembershipCardTierUpdateRequest updateReq = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.valueOf(450000.00))
                .durationMonths(12)
                .discountPercentage(7)
                .active(true)
                .build();
        membershipCardService.updateTier(goldTier.getId(), updateReq, managerUser);

        // 2. Member purchases card online
        MemberCardPurchaseRequest purchaseReq = MemberCardPurchaseRequest.builder()
                .memberId(memberProfile.getId())
                .tierId(goldTier.getId())
                .paymentMethod(PaymentMethod.VNPAY)
                .build();
        MemberCardResponse response = membershipCardService.purchaseCard(purchaseReq, memberUser);

        // Must NOT be immediately active; must be PENDING_PAYMENT with linked payment created
        assertNotNull(response);
        assertEquals(CardStatus.PENDING_PAYMENT, response.getStatus());
        assertEquals(0, BigDecimal.valueOf(450000.00).compareTo(response.getPricePaid()));
        assertNotNull(response.getPaymentId(), "Pending payment card must have linked payment created");

        // Restore tier price
        goldTier.setPrice(originalPrice);
        cardTierRepository.saveAndFlush(goldTier);
    }

    @Test
    @DisplayName("R3-04: Tier ranking depends on explicit rank, proving price edits cannot flip upgrade rules")
    void testRankRule_silverRequestAfterGoldPriceRaise_doesNotReplaceGold() {
        // Create custom Gold (rank 3) and Silver (rank 2) tiers
        MembershipCardTier goldTier = cardTierRepository.save(MembershipCardTier.builder()
                .code("GOLD-RANK-" + MARKER)
                .name("Gold Rank " + MARKER)
                .price(BigDecimal.valueOf(500000.00))
                .durationMonths(12)
                .discountPercentage(10)
                .tierRank(3)
                .isActive(true)
                .build());

        MembershipCardTier silverTier = cardTierRepository.save(MembershipCardTier.builder()
                .code("SILVER-RANK-" + MARKER)
                .name("Silver Rank " + MARKER)
                .price(BigDecimal.valueOf(550000.00))
                .durationMonths(12)
                .discountPercentage(5)
                .tierRank(2)
                .isActive(true)
                .build());

        // 1. Member purchases Gold card at 500k at desk -> ACTIVE
        MemberCardPurchaseRequest goldPurchase = MemberCardPurchaseRequest.builder()
                .memberId(memberProfile.getId())
                .tierId(goldTier.getId())
                .build();
        MemberCardResponse activeGold = membershipCardService.purchaseCard(goldPurchase, managerUser);
        assertEquals(CardStatus.ACTIVE, activeGold.getStatus());

        // 2. Manager edits Gold live price: lowers Gold price to 400,000 (below Silver at 550,000)
        MembershipCardTierUpdateRequest goldUpdate = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.valueOf(400000.00))
                .durationMonths(12)
                .discountPercentage(10)
                .active(true)
                .build();
        membershipCardService.updateTier(goldTier.getId(), goldUpdate, managerUser);

        // 3. Member requests Silver (live 550,000)
        // Even though Silver price (550k) > Gold current price (400k), Silver has lower rank (2 < 3) -> NOT an upgrade!
        MemberCardPurchaseRequest silverPurchase = MemberCardPurchaseRequest.builder()
                .memberId(memberProfile.getId())
                .tierId(silverTier.getId())
                .build();
        MemberCardResponse silverCard = membershipCardService.purchaseCard(silverPurchase, managerUser);

        assertNotNull(silverCard);
        // Full price of Silver tier charged
        assertEquals(0, BigDecimal.valueOf(550000.00).compareTo(silverCard.getPricePaid()));
        // Queued after Gold card ends
        assertEquals(activeGold.getEndDate().plusDays(1), silverCard.getStartDate());

        // Active Gold card is NOT replaced because price edits cannot flip rank upgrade rules
        MemberCard reloadedGold = memberCardRepository.findById(activeGold.getId()).orElseThrow();
        assertEquals(CardStatus.ACTIVE, reloadedGold.getStatus(), "Gold card must NOT be replaced when requesting lower-ranked tier even if lower tier is more expensive");
    }

    @Test
    @DisplayName("R3-03: Migration V21 to V22 backfills legacy fixture data including upgrade chain A -> B -> C and succeeds idempotently")
    void testMigrationV22_backfillUpgradeChainAndIdempotency() throws Exception {
        MembershipCardTier goldTier = cardTierRepository.findByCode("GOLD").orElseThrow();
        MembershipCardTier vipTier = cardTierRepository.findByCode("VIP").orElseThrow();

        LocalDate endDec = LocalDate.of(2026, 12, 31);
        // Seed legacy fixture data without V22 upgrade columns populated
        // Card A: Initial purchase (REPLACED), started Jan 1, ends Dec 31, paid 300k (full GOLD price)
        MemberCard cardA = memberCardRepository.save(MemberCard.builder()
                .cardCode("CARD-MIG-A-" + MARKER)
                .member(memberProfile)
                .tier(goldTier)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(endDec)
                .status(CardStatus.REPLACED)
                .pricePaid(goldTier.getPrice())
                .upgradeRequest(false)
                .replacesCard(null)
                .build());

        // Card B: Mid-term upgrade from A (REPLACED), started Mar 1, ends Dec 31, paid 150k (upgrade fee < VIP 600k)
        MemberCard cardB = memberCardRepository.save(MemberCard.builder()
                .cardCode("CARD-MIG-B-" + MARKER)
                .member(memberProfile)
                .tier(vipTier)
                .startDate(LocalDate.of(2026, 3, 1))
                .endDate(endDec)
                .status(CardStatus.REPLACED)
                .pricePaid(BigDecimal.valueOf(150000.00))
                .upgradeRequest(false)
                .replacesCard(null)
                .build());

        // Card C: Subsequent upgrade from B (ACTIVE), started Jun 1, ends Dec 31, paid 150k (upgrade fee < VIP 600k)
        MemberCard cardC = memberCardRepository.save(MemberCard.builder()
                .cardCode("CARD-MIG-C-" + MARKER)
                .member(memberProfile)
                .tier(vipTier)
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(endDec)
                .status(CardStatus.ACTIVE)
                .pricePaid(BigDecimal.valueOf(150000.00))
                .upgradeRequest(false)
                .replacesCard(null)
                .build());

        // Execute V22 migration script with multiline regex (?im)^\s*GO\s*$
        ClassPathResource resource = new ClassPathResource("db/migration/catalog/V22__add_upgrade_tracking_to_member_card.sql");
        String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        String[] batches = sql.split("(?im)^\\s*GO\\s*$");
        for (String batch : batches) {
            String trimmed = batch.trim();
            if (!trimmed.isEmpty()) {
                jdbcTemplate.execute(trimmed);
            }
        }

        // Assert backfill results
        MemberCard reloadedA = memberCardRepository.findById(cardA.getId()).orElseThrow();
        assertFalse(reloadedA.isUpgradeRequest(), "Initial card A was not purchased as upgrade difference");
        assertNull(reloadedA.getReplacesCard(), "Initial card A does not replace any card");

        MemberCard reloadedB = memberCardRepository.findById(cardB.getId()).orElseThrow();
        assertTrue(reloadedB.isUpgradeRequest(), "Card B paid 150k < VIP 600k, must be marked as upgrade");
        assertNotNull(reloadedB.getReplacesCard(), "Card B must be linked to predecessor card A");
        assertEquals(cardA.getId(), reloadedB.getReplacesCard().getId(), "Card B must link to card A");

        MemberCard reloadedC = memberCardRepository.findById(cardC.getId()).orElseThrow();
        assertTrue(reloadedC.isUpgradeRequest(), "Card C paid 150k < VIP 600k, must be marked as upgrade");
        assertNotNull(reloadedC.getReplacesCard(), "Card C must be linked to immediate predecessor card B");
        assertEquals(cardB.getId(), reloadedC.getReplacesCard().getId(), "Card C must link to card B");

        // Re-execute to verify idempotency
        for (String batch : batches) {
            String trimmed = batch.trim();
            if (!trimmed.isEmpty()) {
                jdbcTemplate.execute(trimmed);
            }
        }
    }

    @Test
    @DisplayName("R3-05: Legacy upgrade card with unresolved replaces_card_id fails closed on upgrade attempt")
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
    @DisplayName("R3-05: Unique filtered index ux_member_card_replaces_in_force rejects concurrent double replacement of in-force card")
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
    @DisplayName("R3-06: Updating STANDARD tier price != 0 returns 400 Bad Request")
    void testUpdateTier_standardPriceChange_returns400() {
        MembershipCardTier standardTier = cardTierRepository.findByCode("STANDARD").orElseThrow();
        MembershipCardTierUpdateRequest req = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.valueOf(100000.00))
                .durationMonths(0)
                .discountPercentage(0)
                .active(true)
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                membershipCardService.updateTier(standardTier.getId(), req, managerUser)
        );
        assertEquals("The STANDARD tier price cannot be changed", ex.getMessage());
    }

    @Test
    @DisplayName("R3-06: Deactivating STANDARD tier returns 400 Bad Request")
    void testUpdateTier_standardDeactivate_returns400() {
        MembershipCardTier standardTier = cardTierRepository.findByCode("STANDARD").orElseThrow();
        MembershipCardTierUpdateRequest req = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.ZERO)
                .durationMonths(0)
                .discountPercentage(0)
                .active(false)
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                membershipCardService.updateTier(standardTier.getId(), req, managerUser)
        );
        assertEquals("The STANDARD tier cannot be deactivated", ex.getMessage());
    }

    @Test
    @DisplayName("R3-06: Updating paid tier price to 0 returns 400 Bad Request")
    void testUpdateTier_paidTierPriceZero_returns400() {
        MembershipCardTier goldTier = cardTierRepository.findByCode("GOLD").orElseThrow();
        MembershipCardTierUpdateRequest req = MembershipCardTierUpdateRequest.builder()
                .price(BigDecimal.ZERO)
                .durationMonths(12)
                .discountPercentage(5)
                .active(true)
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                membershipCardService.updateTier(goldTier.getId(), req, managerUser)
        );
        assertEquals("Price must be greater than zero for paid tiers", ex.getMessage());
    }

    @Test
    @DisplayName("R3-06: Updating sport package price to 0 returns 400 Bad Request")
    void testUpdatePackage_priceZero_returns400() {
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

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                sportPackageService.updatePackage(pkg.getId(), req, managerUser)
        );
        assertEquals("Price must be greater than zero", ex.getMessage());
    }

    @Test
    @DisplayName("R3-06: Updating sport package session minutes to 45 returns 400 Bad Request")
    void testUpdatePackage_sessionMinutes45_returns400() {
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

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                sportPackageService.updatePackage(pkg.getId(), req, managerUser)
        );
        assertEquals("Session minutes must be either 60 or 90", ex.getMessage());
    }
}
