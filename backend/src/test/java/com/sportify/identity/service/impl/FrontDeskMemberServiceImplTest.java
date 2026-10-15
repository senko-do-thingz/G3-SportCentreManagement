package com.sportify.identity.service.impl;

import com.sportify.core.common.CodeFormatter;
import com.sportify.core.exception.ConflictException;
import com.sportify.identity.dto.MemberCreateRequest;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.mapper.FrontDeskMemberMapper;
import com.sportify.identity.repository.MemberProfileRepository;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.service.TemporaryPasswordGenerator;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FrontDeskMemberServiceImplTest {
    @Mock private MemberProfileRepository members;
    @Mock private UserRepository users;
    @Mock private RoleRepository roles;
    @Mock private TemporaryPasswordGenerator passwords;
    @Mock private PasswordEncoder encoder;
    private FrontDeskMemberServiceImpl service;
    private final LocalDate today = LocalDate.of(2026, 10, 15);
    private final MemberCreateRequest request = new MemberCreateRequest(" Alice Smith ", " 0901234567 ", " ALICE@EXAMPLE.COM ");
    private MemberProfile profile;

    @BeforeEach
    void setUp() {
        service = new FrontDeskMemberServiceImpl(members, users, roles,
                Mappers.getMapper(FrontDeskMemberMapper.class), passwords, encoder, new CodeFormatter(),
                Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        profile = MemberProfile.builder().id(7L).memberCode("MEM-0007").userAccount(
                UserAccount.builder().id(7L).fullName("Alice Smith").email("alice@example.com")
                        .phone("0901234567").passwordHash("never-return-this-hash").build()).build();
    }

    @Test
    void create_whenEmailExists_rejectsBeforeGeneratingCredentials() {
        when(users.existsByEmailIgnoreCase("alice@example.com")).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.create(request));
        verifyNoInteractions(passwords, encoder, roles);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void create_whenPhoneExists_rejectsBeforeGeneratingCredentials() {
        when(users.existsByPhone("0901234567")).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.create(request));
        verify(users).existsByEmailIgnoreCase("alice@example.com");
        verifyNoInteractions(passwords, encoder, roles);
    }

    private void stubCreation() {
        when(roles.findByCode("MEMBER")).thenReturn(Optional.of(Role.builder().code("MEMBER").build()));
        when(passwords.generate()).thenReturn("StrongTemporary123!");
        when(encoder.encode("StrongTemporary123!")).thenReturn("encoded-password");
        when(members.getNextMemberCode()).thenReturn(7L);
    }

    @Test
    void create_whenValid_normalizesUsesSequenceHashesPasswordAndReturnsItOnce() {
        stubCreation();
        when(users.saveAndFlush(any())).thenAnswer(call -> {
            UserAccount user = call.getArgument(0);
            user.setId(7L);
            return user;
        });
        when(members.saveAndFlush(any())).thenAnswer(call -> {
            MemberProfile member = call.getArgument(0);
            member.setId(member.getUserAccount().getId());
            return member;
        });
        var response = service.create(request);
        assertEquals("MEM-0007", response.memberCode());
        assertEquals("alice@example.com", response.email());
        assertEquals("0901234567", response.phone());
        assertEquals("Alice Smith", response.fullName());
        assertEquals("StrongTemporary123!", response.temporaryPassword());
        assertFalse(response.toString().contains(response.temporaryPassword()));
        ArgumentCaptor<UserAccount> user = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).saveAndFlush(user.capture());
        assertEquals("encoded-password", user.getValue().getPasswordHash());
        assertEquals("MEMBER", user.getValue().getRole().getCode());
        ArgumentCaptor<MemberProfile> member = ArgumentCaptor.forClass(MemberProfile.class);
        verify(members).saveAndFlush(member.capture());
        assertEquals(today, member.getValue().getJoinedOn());
        assertEquals("BEGINNER", member.getValue().getCurrentLevel());
    }

    @ParameterizedTest
    @ValueSource(ints = {2601, 2627})
    void create_whenConcurrentAccountUniqueConstraintFails_returnsConflict(int errorCode) {
        stubCreation();
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique identity", new SQLException("duplicate", "23000", errorCode)));
        assertThrows(ConflictException.class, () -> service.create(request));
        verify(members, never()).saveAndFlush(any());
    }

    @Test
    void create_whenMemberConstraintFails_returnsConflict() {
        stubCreation();
        when(users.saveAndFlush(any())).thenReturn(profile.getUserAccount());
        when(members.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique member code",
                new ConstraintViolationException("duplicate", new SQLException("duplicate", "23000", 2627), "uq_member_code")));
        assertThrows(ConflictException.class, () -> service.create(request));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(ints = {515, 547, 2628})
    void create_whenIntegrityFailureIsNotUnique_propagatesOriginalException(int errorCode) {
        stubCreation();
        var failure = new DataIntegrityViolationException("invalid member data",
                new ConstraintViolationException("constraint", new SQLException("invalid data", "23000", errorCode), "constraint"));
        when(users.saveAndFlush(any())).thenThrow(failure);
        assertSame(failure, assertThrows(DataIntegrityViolationException.class, () -> service.create(request)));
    }

    @Test
    void create_whenStaffUsesJwt_logsStaffIdAndMemberIdentityWithoutSecretsOrContacts() {
        var staff = UserAccount.builder().id(42L).email("staff@example.com").phone("0909999999")
                .role(Role.builder().code("RECEPTIONIST").build()).build();
        var principal = new CustomUserDetails(staff);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        assertSafeCreationLog("staff-42");
    }

    @Test
    void create_whenStaffHasNonContactUsername_logsActingUsername() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("receptionist", null, List.of()));
        assertSafeCreationLog("receptionist");
    }

    @Test
    void create_whenFallbackUsernameIsAnEmail_redactsItFromLog() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("staff@example.com", null, List.of()));
        assertSafeCreationLog("authenticated-staff");
    }

    private void assertSafeCreationLog(String expectedActor) {
        stubCreation();
        when(users.saveAndFlush(any())).thenReturn(profile.getUserAccount());
        when(members.saveAndFlush(any())).thenReturn(profile);
        Logger logger = (Logger) LoggerFactory.getLogger(FrontDeskMemberServiceImpl.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var response = service.create(request);
            assertEquals(1, appender.list.size());
            String message = appender.list.getFirst().getFormattedMessage();
            assertEquals("Staff " + expectedActor + " created member id=7 code=MEM-0007", message);
            for (String secret : List.of(response.temporaryPassword(), "encoded-password", "never-return-this-hash",
                    "staff@example.com", "0909999999", request.email(), request.phone())) {
                assertFalse(message.contains(secret));
                assertFalse(response.toString().contains(secret));
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
