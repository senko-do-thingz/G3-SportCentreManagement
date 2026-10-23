package com.sportify.catalog.service;

import com.sportify.catalog.dto.FacilityResponse;
import com.sportify.catalog.dto.PublicFacilityResponse;

import java.util.List;

public interface FacilityService {
    List<FacilityResponse> getFacilities();

    /** Active facilities only, ordered by name then id, without audit or code fields. */
    List<PublicFacilityResponse> getPublicFacilities();
}
