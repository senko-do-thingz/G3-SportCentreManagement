package com.sportify.catalog.service;

import com.sportify.catalog.dto.FacilityResponse;

import java.util.List;

public interface FacilityService {
    List<FacilityResponse> getFacilities();
}
