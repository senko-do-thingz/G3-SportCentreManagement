package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.AgeGroupResponse;
import com.sportify.catalog.entity.AgeGroup;
import com.sportify.catalog.repository.AgeGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgeGroupServiceImplTest {

    @Mock
    private AgeGroupRepository ageGroupRepository;

    @InjectMocks
    private AgeGroupServiceImpl ageGroupService;

    private AgeGroup ageGroup1;
    private AgeGroup ageGroup2;

    @BeforeEach
    void setUp() {
        ageGroup1 = AgeGroup.builder()
                .id(1L)
                .code("KIDS_6_12")
                .label("Kids 6-12")
                .minAge(6)
                .maxAge(12)
                .displayOrder(1)
                .isActive(true)
                .build();

        ageGroup2 = AgeGroup.builder()
                .id(2L)
                .code("AGES_16_PLUS")
                .label("Ages 16+")
                .minAge(16)
                .maxAge(null)
                .displayOrder(2)
                .isActive(true)
                .build();
    }

    @Test
    void getActiveAgeGroups_shouldReturnMappedList() {
        when(ageGroupRepository.findByIsActiveTrueOrderByDisplayOrderAscIdAsc())
                .thenReturn(List.of(ageGroup1, ageGroup2));

        List<AgeGroupResponse> result = ageGroupService.getActiveAgeGroups();

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).id());
        assertEquals("KIDS_6_12", result.get(0).code());
        assertEquals("Kids 6-12", result.get(0).label());
        assertEquals(6, result.get(0).minAge());
        assertEquals(12, result.get(0).maxAge());

        assertEquals(2L, result.get(1).id());
        assertEquals("AGES_16_PLUS", result.get(1).code());
        assertEquals(16, result.get(1).minAge());
        assertNull(result.get(1).maxAge());
    }
}
