package com.sportify.identity.service.impl;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.identity.dto.ActivityLogQuery;
import com.sportify.identity.dto.ActivityLogResponse;
import com.sportify.identity.repository.ActivityLogRepository;
import com.sportify.identity.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@PreAuthorize("hasRole('MANAGER')")
public class ActivityLogServiceImpl implements ActivityLogService {
    private final ActivityLogRepository logs;

    @Override
    public PageResponse<ActivityLogResponse> list(Long actorId, String action, String from, String to,
            String search, int page, int size) {
        var query = ActivityLogQuery.parse(actorId, action, from, to, search, page, size);
        return PageResponse.from(logs.search(query));
    }
}
