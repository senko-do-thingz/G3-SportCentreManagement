package com.sportify.identity.service.impl;

import com.sportify.core.common.FrontDeskPaging;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.identity.mapper.MemberSearchMapper;
import com.sportify.identity.repository.MemberProfileRepository;
import com.sportify.identity.repository.MemberSearchRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.*;

import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberSearchServiceImplTest {
    @Mock private MemberProfileRepository members;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneOffset.UTC);

    private MemberSearchServiceImpl service() {
        return new MemberSearchServiceImpl(members, Mappers.getMapper(MemberSearchMapper.class), clock);
    }

    @Test
    void searchMembers_whenQueryHasWhitespaceAndWildcards_usesEscapedLiteralPattern() {
        var pageable = PageRequest.of(0, 20);
        when(members.searchFrontDesk(any(), any(), any())).thenReturn(Page.empty());
        service().searchMembers("  A%_[\\  ", pageable);
        verify(members).searchFrontDesk(eq("%A\\%\\_\\[\\\\%"), eq(LocalDate.now(clock)),
                eq(FrontDeskPaging.search(pageable)));
    }

    @Test
    void searchMembers_whenNullOrBlank_usesNoFilter() {
        when(members.searchFrontDesk(isNull(), any(), any())).thenReturn(Page.empty());
        service().searchMembers(null, PageRequest.of(0, 20));
        service().searchMembers("   ", PageRequest.of(0, 20));
        verify(members, times(2)).searchFrontDesk(isNull(), any(), any());
    }

    @Test
    void searchMembers_whenPageIsLarge_capsSizeAndWhitelistsSort() {
        when(members.searchFrontDesk(any(), any(), any())).thenReturn(Page.empty());
        service().searchMembers("Alice", PageRequest.of(2, 500, Sort.by(Sort.Direction.DESC, "fullName")));
        verify(members).searchFrontDesk(eq("%Alice%"), any(),
                eq(PageRequest.of(2, 100, Sort.by(Sort.Order.desc("u.fullName"), Sort.Order.asc("m.id")))));
        assertThrows(BusinessRuleException.class, () ->
                service().searchMembers(null, PageRequest.of(0, 10, Sort.by("userAccount.passwordHash"))));
    }

    @Test
    void searchMembers_whenRowMatches_returnsTierStatusAndExistingFields() {
        MemberSearchRow row = mock(MemberSearchRow.class);
        when(row.getUserId()).thenReturn(7L);
        when(row.getMemberCode()).thenReturn("MEM-0007");
        when(row.getFullName()).thenReturn("Alice Smith");
        when(row.getCardTier()).thenReturn("GOLD");
        when(row.getStatus()).thenReturn("ACTIVE");
        when(members.searchFrontDesk(any(), any(), any())).thenReturn(new PageImpl<>(List.of(row)));
        var response = service().searchMembers("Alice", PageRequest.of(0, 20)).getContent().getFirst();
        assertEquals("MEM-0007", response.getMemberCode());
        assertEquals("Alice Smith", response.getFullName());
        assertEquals("GOLD", response.getCardTier());
        assertEquals("ACTIVE", response.getStatus());
    }
}
