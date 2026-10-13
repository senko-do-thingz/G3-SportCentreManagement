package com.sportify.catalog.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.AbstractIntegrationTest;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for task BE-03 Manager Catalog and Pricing features.
 * Extends AbstractIntegrationTest (uses Testcontainers; skipped locally when Docker is unavailable).
 */
@SpringBootTest
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
        memberProfile = memberProfileRepository.save(MemberProfile.builder()
                .userAccount(memberAccount)
                .memberCode("MEM-" + MARKER)
                .build());
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM sport_package_registration WHERE registration_code LIKE '%" + MARKER + "%'");
        jdbcTemplate.update("DELETE FROM member_card WHERE card_code LIKE '%" + MARKER + "%'");
        jdbcTemplate.update("DELETE FROM activity_log WHERE summary LIKE '%" + MARKER + "%' OR entity_code LIKE '%" + MARKER + "%' OR details LIKE '%" + MARKER + "%'");
        jdbcTemplate.update("DELETE FROM sport_package WHERE code LIKE '%" + MARKER + "%'");
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
                .description("Updated description")
                .build();

        SportPackageResponse response = sportPackageService.updatePackage(pkg.getId(), request, managerUser);
        assertNotNull(response);
        assertEquals("Updated Audit Pkg " + MARKER, response.getName());
        assertEquals(BigDecimal.valueOf(220000.00), response.getPriceAmount());

        // Verify entity persisted in DB
        SportPackage reloaded = sportPackageRepository.findById(pkg.getId()).orElseThrow();
        assertEquals("Updated Audit Pkg " + MARKER, reloaded.getName());
        assertEquals(BigDecimal.valueOf(220000.00), reloaded.getPriceAmount());
        assertEquals(15, reloaded.getSessionCount());
        assertEquals(45, reloaded.getDurationDays());
        assertEquals(90, reloaded.getSessionMinutes());
        assertFalse(reloaded.getIsActive());
        assertEquals("Updated description", reloaded.getDescription());

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
                .name("Gold " + MARKER)
                .price(BigDecimal.valueOf(380000.00))
                .durationMonths(12)
                .discountPercentage(7)
                .description("Updated gold tier description " + MARKER)
                .active(true)
                .build();

        membershipCardService.updateTier(goldTier.getId(), request, managerUser);

        // Verify entity persisted in DB
        MembershipCardTier reloaded = cardTierRepository.findById(goldTier.getId()).orElseThrow();
        assertEquals("Gold " + MARKER, reloaded.getName());
        assertEquals(BigDecimal.valueOf(380000.00), reloaded.getPrice());
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
                .originalPrice(BigDecimal.valueOf(500000.00))
                .discountPercentage(0)
                .paidAmount(BigDecimal.valueOf(500000.00))
                .status(PackageRegistrationStatus.ACTIVE)
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
                .name(goldTier.getName())
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
}
