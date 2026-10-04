package com.sportify.identity.service;

import com.sportify.identity.dto.MemberSearchResponse;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.MemberProfileRepository;
import com.sportify.identity.service.impl.MemberSearchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MemberSearchServiceTest {

    @Mock
    private MemberProfileRepository memberProfileRepository;

    @InjectMocks
    private MemberSearchServiceImpl memberSearchService;

    private MemberProfile profile;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        UserAccount user = new UserAccount();
        user.setId(1L);
        user.setFullName("John Doe");
        user.setEmail("john@example.com");
        user.setPhone("123456789");

        profile = new MemberProfile();
        profile.setId(1L);
        profile.setMemberCode("MEM-0001");
        profile.setUserAccount(user);
        profile.setCurrentLevel("BEGINNER");

        pageable = PageRequest.of(0, 10);
    }

    @Test
    void searchMembers_NullQuery_CallsFindAll() {
        Page<MemberProfile> page = new PageImpl<>(List.of(profile));
        when(memberProfileRepository.findAllWithUser(pageable)).thenReturn(page);

        Page<MemberSearchResponse> res = memberSearchService.searchMembers(null, pageable);

        assertEquals(1, res.getTotalElements());
        assertEquals("John Doe", res.getContent().get(0).getFullName());
        verify(memberProfileRepository).findAllWithUser(pageable);
        verify(memberProfileRepository, never()).search(any(), any());
    }

    @Test
    void searchMembers_BlankQuery_CallsFindAll() {
        Page<MemberProfile> page = new PageImpl<>(List.of(profile));
        when(memberProfileRepository.findAllWithUser(pageable)).thenReturn(page);

        Page<MemberSearchResponse> res = memberSearchService.searchMembers("   ", pageable);

        assertEquals(1, res.getTotalElements());
        verify(memberProfileRepository).findAllWithUser(pageable);
        verify(memberProfileRepository, never()).search(any(), any());
    }

    @Test
    void searchMembers_WithQuery_CallsSearch() {
        Page<MemberProfile> page = new PageImpl<>(List.of(profile));
        when(memberProfileRepository.search("%john%", pageable)).thenReturn(page);

        Page<MemberSearchResponse> res = memberSearchService.searchMembers(" John ", pageable);

        assertEquals(1, res.getTotalElements());
        verify(memberProfileRepository).search("%john%", pageable);
        verify(memberProfileRepository, never()).findAllWithUser(any());
    }
}
