package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.FacilityResponse;
import com.sportify.catalog.entity.Facility;
import com.sportify.catalog.repository.FacilityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacilityServiceImplTest {
    @Mock private FacilityRepository repository;
    @InjectMocks private FacilityServiceImpl service;

    @Test
    void directory_keepsPersistedIdentifiersNamesAndInactiveStateUsingStableDatabaseOrder() {
        when(repository.findAll(Sort.by("name", "id"))).thenReturn(List.of(
                Facility.builder().id(50L).name("Court A").isActive(false).build(),
                Facility.builder().id(70L).name("Court A").isActive(true).build()));
        assertEquals(List.of(new FacilityResponse(50L, "Court A", false),
                new FacilityResponse(70L, "Court A", true)), service.getFacilities());
        verify(repository).findAll(Sort.by("name", "id"));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void emptyTable_returnsEmptyDirectoryWithoutWrites() {
        when(repository.findAll(Sort.by("name", "id"))).thenReturn(List.of());
        assertEquals(List.of(), service.getFacilities());
        verify(repository).findAll(Sort.by("name", "id"));
        verifyNoMoreInteractions(repository);
    }
}
