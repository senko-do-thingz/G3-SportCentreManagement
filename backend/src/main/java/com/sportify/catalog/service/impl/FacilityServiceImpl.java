package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.FacilityResponse;
import com.sportify.catalog.dto.PublicFacilityResponse;
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
                .map(facility -> new FacilityResponse(
                        facility.getId(),
                        facility.getName(),
                        Boolean.TRUE.equals(facility.getIsActive()),
                        facility.getSport() == null ? null : facility.getSport().getId(),
                        resolveFacilityCapacity(facility.getCode(), facility.getName(), facility.getCapacity())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublicFacilityResponse> getPublicFacilities() {
        // The facility table is small, so the shared sorted read is filtered here instead of adding a query.
        return facilityRepository.findAll(Sort.by("name", "id")).stream()
                .filter(facility -> Boolean.TRUE.equals(facility.getIsActive()))
                .map(facility -> new PublicFacilityResponse(facility.getId(), facility.getName(), facility.getFacilityType(),
                        facility.getSport() == null ? null : facility.getSport().getId(),
                        resolveFacilityCapacity(facility.getCode(), facility.getName(), facility.getCapacity())))
                .toList();
    }

    private Integer resolveFacilityCapacity(String code, String name, Integer capacity) {
        if (capacity != null) {
            return capacity;
        }
        if ("BB_COURT_A".equalsIgnoreCase(code) || "BB_COURT_B".equalsIgnoreCase(code)
                || "Basketball Court A".equalsIgnoreCase(name) || "Basketball Court B".equalsIgnoreCase(name)) {
            return 10;
        }
        if ("INDOOR_POOL".equalsIgnoreCase(code) || "Indoor Pool".equalsIgnoreCase(name)) {
            return 15;
        }
        return null;
    }
}
