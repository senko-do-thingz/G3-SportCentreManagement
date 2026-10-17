package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.FacilityResponse;
import com.sportify.catalog.repository.FacilityRepository;
import com.sportify.catalog.service.FacilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FacilityServiceImpl implements FacilityService {
    private final FacilityRepository facilityRepository;

    @Override
    @Transactional(readOnly = true)
    public List<FacilityResponse> getFacilities() {
        // Retain inactive facilities for historical calendar filters; saves still require active assignments.
        return facilityRepository.findAll(Sort.by("name", "id")).stream()
                .map(facility -> new FacilityResponse(facility.getId(), facility.getName(),
                        Boolean.TRUE.equals(facility.getIsActive())))
                .toList();
    }
}
