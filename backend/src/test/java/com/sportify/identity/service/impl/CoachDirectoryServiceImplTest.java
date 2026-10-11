package com.sportify.identity.service.impl;

import com.sportify.identity.dto.CoachOptionResponse;
import com.sportify.identity.entity.CoachProfile;
import com.sportify.identity.entity.CoachSport;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.CoachProfileRepository;
import com.sportify.identity.repository.CoachSportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoachDirectoryServiceImplTest {

    @Mock
    private CoachProfileRepository coachProfileRepository;
    @Mock
    private CoachSportRepository coachSportRepository;

    @InjectMocks
    private CoachDirectoryServiceImpl coachDirectoryService;

    private CoachProfile coach1;
    private CoachProfile coach2;

    @BeforeEach
    void setUp() {
        UserAccount acc1 = UserAccount.builder()
                .id(10L)
                .fullName("Daniel Smith")
                .role(Role.builder().code("COACH").build())
                .status("ACTIVE")
                .build();
        coach1 = CoachProfile.builder()
                .id(10L)
                .userAccount(acc1)
                .headline("Head Football Coach")
                .specialties("Passing, Tactics")
                .yearsExperience(6)
                .build();

        UserAccount acc2 = UserAccount.builder()
                .id(11L)
                .fullName("Emma Wilson")
                .role(Role.builder().code("COACH").build())
                .status("ACTIVE")
                .build();
        coach2 = CoachProfile.builder()
                .id(11L)
                .userAccount(acc2)
                .headline("Swimming Coach")
                .specialties("Freestyle, Butterfly")
                .yearsExperience(4)
                .build();
    }

    @Test
    void getActiveCoaches_withoutSportId_shouldReturnAllWithSports() {
        when(coachProfileRepository.findActiveCoaches()).thenReturn(List.of(coach1, coach2));
        when(coachSportRepository.findByIdCoachIdIn(List.of(10L, 11L))).thenReturn(List.of(
                CoachSport.of(10L, 1L),
                CoachSport.of(10L, 2L),
                CoachSport.of(11L, 3L)
        ));

        List<CoachOptionResponse> result = coachDirectoryService.getActiveCoaches(null);

        assertEquals(2, result.size());
        assertEquals(10L, result.get(0).id());
        assertEquals("Daniel Smith", result.get(0).fullName());
        assertEquals("Head Football Coach", result.get(0).headline());
        assertEquals(List.of(1L, 2L), result.get(0).sportIds());

        assertEquals(11L, result.get(1).id());
        assertEquals("Emma Wilson", result.get(1).fullName());
        assertEquals(List.of(3L), result.get(1).sportIds());
    }

    @Test
    void getActiveCoaches_withSportId_shouldFilterBySport() {
        when(coachProfileRepository.findActiveCoachesBySport(1L)).thenReturn(List.of(coach1));
        when(coachSportRepository.findByIdCoachIdIn(List.of(10L))).thenReturn(List.of(
                CoachSport.of(10L, 1L)
        ));

        List<CoachOptionResponse> result = coachDirectoryService.getActiveCoaches(1L);

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).id());
        assertEquals("Daniel Smith", result.get(0).fullName());
        assertEquals(List.of(1L), result.get(0).sportIds());
        verify(coachProfileRepository).findActiveCoachesBySport(1L);
    }

    @Test
    void getActiveCoaches_whenNoneFound_shouldReturnEmptyList() {
        when(coachProfileRepository.findActiveCoachesBySport(99L)).thenReturn(List.of());

        List<CoachOptionResponse> result = coachDirectoryService.getActiveCoaches(99L);

        assertTrue(result.isEmpty());
        verifyNoInteractions(coachSportRepository);
    }
}
