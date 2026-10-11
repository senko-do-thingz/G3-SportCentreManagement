package com.sportify.catalog.service;

import com.sportify.catalog.dto.ManagerClassResponse;
import com.sportify.catalog.dto.PageResponse;
import com.sportify.catalog.dto.SportClassRequest;
import com.sportify.catalog.entity.ClassLevel;
import com.sportify.identity.entity.UserAccount;

/**
 * Manager class management (screens F2-01 "Class Management" and F2-02 "Create / Edit Class").
 */
public interface ManagerClassService {

    PageResponse<ManagerClassResponse> listClasses(Long sportId, ClassLevel level, Long coachId, Boolean active,
                                                   String search, int page, int size);

    ManagerClassResponse getClass(Long id);

    ManagerClassResponse createClass(SportClassRequest request, UserAccount actor);

    ManagerClassResponse updateClass(Long id, SportClassRequest request, UserAccount actor);

    /**
     * Activates or deactivates a class. Deactivating a class that still has confirmed bookings on
     * sessions that have not started yet is rejected with a business rule error (HTTP 400).
     */
    ManagerClassResponse changeStatus(Long id, boolean active, UserAccount actor);
}
