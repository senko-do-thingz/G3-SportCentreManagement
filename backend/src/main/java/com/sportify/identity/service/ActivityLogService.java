package com.sportify.identity.service;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.identity.dto.ActivityLogResponse;

public interface ActivityLogService {
    PageResponse<ActivityLogResponse> list(Long actorId, String action, String from, String to,
            String search, int page, int size);
}
