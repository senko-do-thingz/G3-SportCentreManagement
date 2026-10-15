package com.sportify.catalog.service;

import com.sportify.catalog.dto.SportResponse;

import java.util.List;

public interface SportService {

    /** Active sports ordered by display order (public). */
    List<SportResponse> getActiveSports();
}
