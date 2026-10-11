package com.sportify.catalog.service;

import com.sportify.catalog.dto.AgeGroupResponse;

import java.util.List;

/**
 * Age group lookup for form dropdowns.
 */
public interface AgeGroupService {

    /** Active age groups ordered by display order. */
    List<AgeGroupResponse> getActiveAgeGroups();
}
