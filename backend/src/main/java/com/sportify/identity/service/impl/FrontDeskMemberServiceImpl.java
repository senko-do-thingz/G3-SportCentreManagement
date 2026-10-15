package com.sportify.identity.service.impl;

import com.sportify.catalog.entity.CardStatus;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.mapper.FrontDeskMapper;
import com.sportify.catalog.repository.*;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.dto.*;
import com.sportify.identity.entity.*;
import com.sportify.identity.mapper.FrontDeskMemberMapper;
import com.sportify.identity.mapper.MemberSearchMapper;
import com.sportify.identity.repository.*;
import com.sportify.identity.service.FrontDeskMemberService;
import com.sportify.identity.service.TemporaryPasswordGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class FrontDeskMemberServiceImpl implements FrontDeskMemberService {
    private final MemberProfileRepository members;
    private final UserRepository users;
    private final RoleRepository roles;
    private final MemberCardRepository cards;
    private final SportPackageRegistrationRepository registrations;
    private final CheckInRepository checkIns;
    private final FrontDeskMapper catalogMapper;
    private final FrontDeskMemberMapper mapper;
    private final MemberSearchMapper searchMapper;
    private final TemporaryPasswordGenerator passwords;
    private final PasswordEncoder encoder;
    private final CodeFormatter codes;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public MemberOverviewResponse overview(Long memberId) {
        MemberProfile member = members.findWithUserById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));
        LocalDate today = LocalDate.now(clock);
        var current = cards.findCurrentForOverview(memberId, today, PageRequest.of(0, 1)).stream()
                .findFirst().map(catalogMapper::toCard).orElse(null);
        var pending = cards.findFirstByMemberIdAndStatusOrderByCreatedAtAscIdAsc(memberId, CardStatus.PENDING_PAYMENT)
                .map(catalogMapper::toCard).orElse(null);
        var packages = registrations.findForOverview(memberId, today);
        var activePackages = packages.stream().filter(r -> r.getStatus() == PackageRegistrationStatus.ACTIVE)
                .map(catalogMapper::toPackage).toList();
        var pendingPackages = packages.stream().filter(r -> r.getStatus() == PackageRegistrationStatus.PENDING_PAYMENT)
                .map(catalogMapper::toPackage).toList();
        var recent = checkIns.findTop5ByMemberIdOrderByCheckedInAtDescIdDesc(memberId).stream()
                .map(catalogMapper::toCheckIn).toList();
        return mapper.toOverview(searchMapper.toProfile(member, current == null ? null : current.tierCode()),
                current, pending, activePackages, pendingPackages, recent);
    }

    @Override
    @Transactional
    public MemberCreateResponse create(MemberCreateRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("Email is already registered");
        }
        if (users.existsByPhone(request.phone())) {
            throw new ConflictException("Phone number is already registered");
        }
        Role role = roles.findByCode("MEMBER").orElseThrow(() -> new IllegalStateException("Default role not found"));
        String temporaryPassword = passwords.generate();
        String code = codes.formatMemberCode(members.getNextMemberCode());
        UserAccount user = mapper.toUser(request, role, encoder.encode(temporaryPassword));
        try {
            UserAccount saved = users.saveAndFlush(user);
            MemberProfile member = members.saveAndFlush(mapper.toMember(saved, code, LocalDate.now(clock)));
            return mapper.toCreated(member, temporaryPassword);
        } catch (DataIntegrityViolationException ex) {
            // Flush inside the transaction so database uniqueness races use the standard 409 response.
            throw new ConflictException("The request conflicts with existing member data");
        }
    }
}
