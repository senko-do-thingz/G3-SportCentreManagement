package com.sportify.identity.service.impl;

import com.sportify.core.common.CodeFormatter;
import com.sportify.core.exception.ConflictException;
import com.sportify.identity.dto.MemberCreateRequest;
import com.sportify.identity.dto.MemberCreateResponse;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.mapper.FrontDeskMemberMapper;
import com.sportify.identity.repository.MemberProfileRepository;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.service.FrontDeskMemberService;
import com.sportify.identity.service.TemporaryPasswordGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class FrontDeskMemberServiceImpl implements FrontDeskMemberService {
    private static final String MEMBER_ROLE = "MEMBER";
    private final MemberProfileRepository members;
    private final UserRepository users;
    private final RoleRepository roles;
    private final FrontDeskMemberMapper mapper;
    private final TemporaryPasswordGenerator passwords;
    private final PasswordEncoder encoder;
    private final CodeFormatter codes;
    private final Clock clock;

    @Override
    @Transactional
    public MemberCreateResponse create(MemberCreateRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("Email is already registered");
        }
        if (users.existsByPhone(request.phone())) {
            throw new ConflictException("Phone number is already registered");
        }
        Role role = roles.findByCode(MEMBER_ROLE).orElseThrow(() -> new IllegalStateException("Default role not found"));
        String temporaryPassword = passwords.generate();
        String code = codes.formatMemberCode(members.getNextMemberCode());
        UserAccount user = mapper.toUser(request, role, encoder.encode(temporaryPassword));
        try {
            UserAccount saved = users.saveAndFlush(user);
            MemberProfile member = members.saveAndFlush(mapper.toMember(saved, code, LocalDate.now(clock)));
            // Both writes have been flushed; this accountability log runs before transaction commit.
            log.info("Staff {} created member id={} code={}", actingStaffUsername(), member.getId(), member.getMemberCode());
            return mapper.toCreated(member, temporaryPassword);
        } catch (DataIntegrityViolationException ex) {
            if (UniqueViolationClassifier.isUniqueViolation(ex)) {
                throw new ConflictException("The request conflicts with existing member data");
            }
            throw ex;
        }
    }

    private String actingStaffUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "unauthenticated";
        }
        if (authentication.getPrincipal() instanceof CustomUserDetails details) {
            // JWT usernames are email addresses. Record the staff id instead of personal contact data.
            return "staff-" + details.getUserAccount().getId();
        }
        String username = authentication.getName();
        return username != null && username.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")
                ? username : "authenticated-staff";
    }
}
