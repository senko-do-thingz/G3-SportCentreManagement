package com.sportify.identity.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.catalog.dto.PageResponse;
import com.sportify.catalog.dto.SessionSearchQuery;
import com.sportify.catalog.repository.SportRepository;
import com.sportify.catalog.service.MemberScheduleService;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.*;
import com.sportify.identity.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static com.sportify.catalog.test.ScheduleTestData.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoachPublicProfileServiceImplTest {
    @Mock CoachProfileRepository coaches;
    @Mock CoachSportRepository coachSports;
    @Mock CoachCertificationRepository certifications;
    @Mock SportRepository sports;
    @Mock MemberScheduleService schedule;
    CoachPublicProfileServiceImpl service;
    UserAccount actor;

    @BeforeEach void setUp() {
        service = new CoachPublicProfileServiceImpl(coaches, coachSports, certifications, sports, schedule);
        actor = account("MEMBER", 2);
    }

    private CoachProfile existing() {
        var coach = coach();
        when(coaches.findWithAccountById(20L)).thenReturn(Optional.of(coach));
        return coach;
    }

    private void upcoming() {
        when(schedule.listSessions(new SessionSearchQuery(null, null, null, null, null, 20L, 0, 20), actor))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
    }

    @Test void publicProfile_showsMultipleSportsCertificationsAndBioWithoutAccountOrDocumentSecrets() throws Exception {
        existing(); upcoming();
        when(coachSports.findByIdCoachIdIn(List.of(20L))).thenReturn(List.of(CoachSport.of(20L, 10L), CoachSport.of(20L, 11L)));
        var other = sport(); other.setId(11L); other.setName("Other sport");
        when(sports.findAllById(List.of(10L, 11L))).thenReturn(List.of(other, sport()));
        when(certifications.findByCoachIdOrderByIdAsc(20L)).thenReturn(List.of(
                CoachCertification.builder().id(1L).name("Coaching certificate").sportId(10L).issuer("Sport Board")
                        .issuedOn(TODAY.minusYears(2)).expiresOn(TODAY.plusYears(1)).isVerified(true).documentUrl("private-document-url").build(),
                CoachCertification.builder().id(2L).name("First aid").isVerified(false).build()));
        var result = service.getProfile(20L, actor);
        assertThat(result.id()).isEqualTo(20L);
        assertThat(result.bio()).isEqualTo("Public coaching biography");
        assertThat(result.sports()).extracting(s -> s.id()).containsExactly(10L, 11L);
        assertThat(result.certifications()).hasSize(2);
        assertThat(result.certifications().getFirst().verified()).isTrue();
        assertThat(result.certifications().getLast().verified()).isFalse();
        var json = new ObjectMapper().findAndRegisterModules().writeValueAsString(result);
        assertThat(json).doesNotContain("private20@example.test", "private-password-hash", "passwordHash", "email",
                "userAccount", "createdAt", "updatedAt", "documentUrl", "private-document-url");
    }

    @Test void nullBioNoSportsNoCertificationsAndNoUpcomingSessionsAreExplicit() {
        existing().setBio(null); upcoming();
        var result = service.getProfile(20L, actor);
        assertThat(result.bio()).isNull();
        assertThat(result.sports()).isEmpty();
        assertThat(result.certifications()).isEmpty();
        assertThat(result.upcomingSessions().content()).isEmpty();
        assertThat(result.upcomingSessions().size()).isEqualTo(20);
        verifyNoInteractions(sports);
    }

    @Test void upcomingSessionsAreBoundedAndRetainMetadataForFurtherDiscovery() {
        existing();
        when(schedule.listSessions(new SessionSearchQuery(null, null, null, null, null, 20L, 0, 20), actor))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 45, 3));
        var result = service.getProfile(20L, actor);
        assertThat(result.upcomingSessions().size()).isEqualTo(20);
        assertThat(result.upcomingSessions().totalElements()).isEqualTo(45);
        assertThat(result.upcomingSessions().totalPages()).isEqualTo(3);
    }

    @ParameterizedTest @ValueSource(strings = {"MISSING", "PRIVATE", "INACTIVE", "WRONG_ROLE", "NO_ACCOUNT", "NO_ROLE"})
    void invisibleProfilesAre404AndNeverLoadPublicAssociations(String scenario) {
        if (!scenario.equals("MISSING")) {
            var coach = existing();
            switch (scenario) {
                case "PRIVATE" -> coach.setIsPublic(false);
                case "INACTIVE" -> coach.getUserAccount().setStatus("INACTIVE");
                case "WRONG_ROLE" -> coach.getUserAccount().setRole(Role.builder().code("MEMBER").build());
                case "NO_ACCOUNT" -> coach.setUserAccount(null);
                case "NO_ROLE" -> coach.getUserAccount().setRole(null);
                default -> throw new IllegalStateException(scenario);
            }
        }
        assertThatThrownBy(() -> service.getProfile(20L, actor)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(coachSports, certifications, sports, schedule);
    }

    @ParameterizedTest @ValueSource(strings = {"MEMBER", "RECEPTIONIST", "MANAGER"})
    void allApprovedRolesCanReadPublicProfile(String role) {
        actor = account(role, 2); existing(); upcoming();
        assertThat(service.getProfile(20L, actor).name()).isEqualTo("Reader 20");
    }

    @ParameterizedTest @ValueSource(strings = {"COACH", "ADMIN", "NULL", "NO_ID", "NO_ROLE", "INACTIVE"})
    void serviceGuardRejectsUnsupportedOrInvalidActorBeforeQuery(String scenario) {
        var reader = account(scenario, 2);
        if (scenario.equals("NULL")) reader = null;
        if (scenario.equals("NO_ID")) reader.setId(null);
        if (scenario.equals("NO_ROLE")) reader.setRole(null);
        if (scenario.equals("INACTIVE")) reader.setStatus("INACTIVE");
        var denied = reader;
        assertThatThrownBy(() -> service.getProfile(20L, denied)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(coaches, coachSports, certifications, sports, schedule);
    }

    @ParameterizedTest @NullSource @ValueSource(longs = {0, -1})
    void invalidIdsAre400WithoutLookup(Long id) {
        assertThatThrownBy(() -> service.getProfile(id, actor)).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(coaches, coachSports, certifications, sports, schedule);
    }
}
