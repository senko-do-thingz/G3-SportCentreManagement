package com.sportify.identity.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.catalog.dto.SportSummaryResponse;
import com.sportify.catalog.entity.*;
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.core.audit.*;
import com.sportify.core.exception.FieldValidationException;
import com.sportify.identity.dto.*;
import com.sportify.identity.entity.*;
import com.sportify.identity.mapper.UserMapper;
import com.sportify.identity.repository.*;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {
    @Mock UserRepository users;
    @Mock MemberProfileRepository members;
    @Mock MemberCardRepository cards;
    @Mock CoachSportRepository coachSports;
    @Mock AuditService audit;
    @Mock EntityManager entityManager;
    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    final Clock clock = Clock.fixed(Instant.parse("2026-10-16T00:00:00Z"), ZoneOffset.UTC);
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    UserServiceImpl service;
    UserAccount user;
    CustomUserDetails principal;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(Mappers.getMapper(UserMapper.class), users, members, cards,
                coachSports, encoder, audit, entityManager, clock);
        user = UserAccount.builder().id(10L).email("self@example.test").fullName("Original Name")
                .phone("0901234567").address("Original address").dateOfBirth(LocalDate.of(2000, 1, 1))
                .role(Role.builder().code("MANAGER").build()).status("ACTIVE")
                .passwordHash(encoder.encode("Original123")).version(1).build();
        // Principal account data may be stale; only its identity is authoritative.
        principal = new CustomUserDetails(UserAccount.builder().id(10L).fullName("Stale name").build());
        when(users.findById(10L)).thenReturn(Optional.of(user));
    }

    MemberProfile member() {
        user.setRole(Role.builder().code("MEMBER").build());
        MemberProfile member = MemberProfile.builder().id(user.getId()).userAccount(user).memberCode("MEM-SELF")
                .mainGoal("BUILD_SKILLS").currentLevel("INTERMEDIATE").staffNote("Private note").build();
        when(members.findById(10L)).thenReturn(Optional.of(member));
        return member;
    }

    @Test
    void memberGoldUsesExistingBoundedCurrentCardQueryAndIncludesIdentity() {
        member();
        var tier = MembershipCardTier.builder().code("GOLD").name("Gold").tierRank(3).build();
        var card = MemberCard.builder().id(41L).tier(tier).status(CardStatus.ACTIVE)
                .startDate(LocalDate.now(clock).minusMonths(1)).endDate(LocalDate.now(clock).plusMonths(1)).build();
        when(cards.findCurrentForOverview(eq(10L), eq(LocalDate.now(clock)), any())).thenAnswer(call -> {
            org.springframework.data.domain.Pageable page = call.getArgument(2);
            assertEquals(1, page.getPageSize());
            return List.of(card);
        });
        var result = service.getCurrentUserProfile(principal);
        assertEquals("MEM-SELF", result.getMemberCode());
        assertEquals("BUILD_SKILLS", result.getMainGoal());
        assertEquals(41L, result.getMembershipCard().id());
        assertEquals("GOLD", result.getMembershipCard().tierCode());
        assertEquals("ACTIVE", result.getMembershipCard().status());
        assertEquals(card.getEndDate(), result.getMembershipCard().endDate());
    }

    @Test
    void defaultStandardAndNoCardPreserveMemberIdentityWithoutFabricatingCard() {
        member();
        var result = service.getCurrentUserProfile(principal);
        assertEquals("MEM-SELF", result.getMemberCode());
        assertNull(result.getMembershipCard());
        assertNull(result.getSports());
    }

    @Test
    void existingPhysicalStandardCardCanBeRepresented() {
        member();
        var card = MemberCard.builder().id(42L).tier(MembershipCardTier.builder().code("STANDARD").name("Standard").build())
                .status(CardStatus.ACTIVE).startDate(LocalDate.now(clock)).build();
        when(cards.findCurrentForOverview(eq(10L), any(), any())).thenReturn(List.of(card));
        assertEquals("STANDARD", service.getCurrentUserProfile(principal).getMembershipCard().tierCode());
    }

    @Test
    void coachGetsOnlyOwnProjectedSports() {
        user.setRole(Role.builder().code("COACH").build());
        when(coachSports.findProfileSports(10L)).thenReturn(List.of(
                new SportSummaryResponse(2L, "BADMINTON", "Badminton"), new SportSummaryResponse(8L, "TENNIS", "Tennis")));
        var result = service.getCurrentUserProfile(principal);
        assertEquals(List.of(2L, 8L), result.getSports().stream().map(SportSummaryResponse::id).toList());
        assertEquals("Tennis", result.getSports().get(1).name());
        assertNull(result.getMemberCode());
        assertNull(result.getMembershipCard());
        verifyNoInteractions(members, cards);
    }

    @Test
    void coachWithoutSportsGetsEmptyList() {
        user.setRole(Role.builder().code("COACH").build());
        when(coachSports.findProfileSports(10L)).thenReturn(List.of());
        assertEquals(List.of(), service.getCurrentUserProfile(principal).getSports());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "MANAGER"})
    void staffReadsFreshCommonFieldsAndDoesNotExposeCredentials(String role) throws Exception {
        user.setRole(Role.builder().code(role).build());
        var result = service.getCurrentUserProfile(principal);
        assertEquals("Original Name", result.getFullName());
        assertEquals(user.getDateOfBirth(), result.getDateOfBirth());
        assertEquals(user.getAddress(), result.getAddress());
        var tree = json.valueToTree(result);
        assertFalse(tree.has("passwordHash"));
        assertFalse(tree.has("version"));
        assertFalse(tree.has("staffNote"));
        assertFalse(tree.toString().contains(user.getPasswordHash()));
        assertNull(result.getSports());
        verifyNoInteractions(members, cards, coachSports);
    }

    @Test
    void missingOptionalDataAndMissingMemberProfileAreSafe() {
        user.setPhone(null);
        user.setAddress(null);
        user.setDateOfBirth(null);
        user.setRole(Role.builder().code("MEMBER").build());
        when(members.findById(10L)).thenReturn(Optional.empty());
        var result = service.getCurrentUserProfile(principal);
        assertNull(result.getPhone());
        assertNull(result.getAddress());
        assertNull(result.getMemberCode());
        verifyNoInteractions(cards);
    }

    @Test
    void profileUpdateTrimsPersistsAllowedFieldsAndPreservesProtectedFields() throws Exception {
        var member = member();
        String hash = user.getPasswordHash();
        UpdateProfileRequest request = json.readValue("""
                {"fullName":"  Updated Person  ","phone":"+84912345678","address":" New address ",
                 "dateOfBirth":"1999-03-01","mainGoal":"COMPETITION","currentLevel":"ADVANCED"}
                """, UpdateProfileRequest.class);
        var result = service.updateCurrentUserProfile(principal, request);
        assertEquals("Updated Person", user.getFullName());
        assertEquals("+84912345678", result.getPhone());
        assertEquals("New address", user.getAddress());
        assertEquals(LocalDate.of(1999, 3, 1), result.getDateOfBirth());
        assertEquals("COMPETITION", member.getMainGoal());
        assertEquals("ADVANCED", member.getCurrentLevel());
        assertEquals("MEM-SELF", member.getMemberCode());
        assertEquals("Private note", member.getStaffNote());
        assertEquals("self@example.test", user.getEmail());
        assertEquals("MEMBER", result.getRole());
        assertEquals("ACTIVE", user.getStatus());
        assertEquals(hash, user.getPasswordHash());
        verify(entityManager).lock(user, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        verify(members, never()).save(any());
    }

    @Test
    void omittedFieldsRemainAndExplicitNullOptionalsClearWithBeginnerDefault() throws Exception {
        var member = member();
        service.updateCurrentUserProfile(principal, json.readValue("{\"address\":null}", UpdateProfileRequest.class));
        assertNull(user.getAddress());
        assertEquals("Original Name", user.getFullName());
        assertEquals("0901234567", user.getPhone());
        assertEquals("INTERMEDIATE", member.getCurrentLevel());
        var result = service.updateCurrentUserProfile(principal, json.readValue("""
                {"phone":null,"dateOfBirth":null,"mainGoal":null,"currentLevel":null}
                """, UpdateProfileRequest.class));
        assertNull(result.getPhone());
        assertNull(result.getDateOfBirth());
        assertNull(result.getMainGoal());
        assertEquals("BEGINNER", result.getCurrentLevel());
    }

    @Test
    void explicitNullNameIsFieldErrorAndDoesNotFlush() throws Exception {
        var ex = assertThrows(FieldValidationException.class, () -> service.updateCurrentUserProfile(principal,
                json.readValue("{\"fullName\":null}", UpdateProfileRequest.class)));
        assertTrue(ex.getDetails().containsKey("fullName"));
        assertEquals("Original Name", user.getFullName());
        verify(users, never()).flush();
    }

    @Test
    void nonMembersCannotWriteTrainingPreferences() throws Exception {
        var ex = assertThrows(FieldValidationException.class, () -> service.updateCurrentUserProfile(principal,
                json.readValue("{\"mainGoal\":\"BUILD_SKILLS\"}", UpdateProfileRequest.class)));
        assertTrue(ex.getDetails().containsKey("mainGoal"));
        verifyNoInteractions(members);
    }

    @Test
    void pre1900BirthDateIsRejectedWithoutMutation() throws Exception {
        assertThrows(FieldValidationException.class, () -> service.updateCurrentUserProfile(principal,
                json.readValue("{\"dateOfBirth\":\"1899-12-31\"}", UpdateProfileRequest.class)));
        assertEquals(LocalDate.of(2000, 1, 1), user.getDateOfBirth());
        verify(users, never()).flush();
    }

    ChangePasswordRequest password(String current, String next, String confirmation) {
        var request = new ChangePasswordRequest();
        request.setCurrentPassword(current);
        request.setNewPassword(next);
        request.setConfirmPassword(confirmation);
        return request;
    }

    @Test
    void passwordChangeStoresBcryptInvalidatesOldAndWritesSafeAudit() {
        service.changeCurrentUserPassword(principal, password("Original123", "Replacement456", "Replacement456"));
        assertTrue(user.getPasswordHash().startsWith("$2"));
        assertNotEquals("Replacement456", user.getPasswordHash());
        assertFalse(encoder.matches("Original123", user.getPasswordHash()));
        assertTrue(encoder.matches("Replacement456", user.getPasswordHash()));
        assertEquals("ACTIVE", user.getStatus());
        assertEquals("Original Name", user.getFullName());
        var event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(event.capture());
        assertEquals(AuditAction.PASSWORD_CHANGED, event.getValue().getAction());
        assertEquals(10L, event.getValue().getEntityId());
        assertSame(user, event.getValue().getActor());
        assertNull(event.getValue().getDetails());
        assertFalse(event.getValue().isRequiresNew());
        assertEquals("Password changed", event.getValue().getSummary());
    }

    @Test
    void wrongCurrentPasswordHasExpectedFieldAndDoesNotMutate() {
        String before = user.getPasswordHash();
        var ex = assertThrows(FieldValidationException.class, () -> service.changeCurrentUserPassword(principal,
                password("Incorrect123", "Replacement456", "Replacement456")));
        assertEquals("Current password is incorrect", ex.getDetails().get("currentPassword"));
        assertEquals(before, user.getPasswordHash());
        verifyNoInteractions(audit);
        verify(users, never()).flush();
    }

    @Test
    void confirmationMismatchAndSamePasswordLeaveHashUnchanged() {
        String before = user.getPasswordHash();
        var mismatch = assertThrows(FieldValidationException.class, () -> service.changeCurrentUserPassword(principal,
                password("Original123", "Replacement456", "Other789")));
        assertEquals("Passwords do not match", mismatch.getDetails().get("confirmPassword"));
        var same = assertThrows(FieldValidationException.class, () -> service.changeCurrentUserPassword(principal,
                password("Original123", "Original123", "Original123")));
        assertTrue(same.getDetails().containsKey("newPassword"));
        assertEquals(before, user.getPasswordHash());
        verifyNoInteractions(audit);
    }

    @Test
    void staleVersionFailurePreventsAuditAndPropagatesForRollback() {
        doThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(UserAccount.class, 10L))
                .when(users).flush();
        assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class,
                () -> service.changeCurrentUserPassword(principal, password("Original123", "Replacement456", "Replacement456")));
        verifyNoInteractions(audit);
    }

    @Test
    void auditFailurePropagatesOutOfTransactionalMethod() {
        doThrow(new IllegalStateException("Audit unavailable")).when(audit).record(any());
        assertThrows(IllegalStateException.class, () -> service.changeCurrentUserPassword(principal,
                password("Original123", "Replacement456", "Replacement456")));
        // Database rollback is asserted with separate committed reads in MyProfileIntegrationTest.
    }

    @Test
    void inactiveAccountCannotReadOrMutateEvenWithStaleActivePrincipal() {
        user.setStatus("INACTIVE");
        assertThrows(DisabledException.class, () -> service.getCurrentUserProfile(principal));
        assertThrows(DisabledException.class, () -> service.updateCurrentUserProfile(principal, new UpdateProfileRequest()));
        assertThrows(DisabledException.class, () -> service.changeCurrentUserPassword(principal,
                password("Original123", "Replacement456", "Replacement456")));
        verifyNoInteractions(audit);
    }
}
