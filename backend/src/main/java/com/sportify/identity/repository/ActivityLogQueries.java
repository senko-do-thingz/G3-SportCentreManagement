package com.sportify.identity.repository;

import com.sportify.identity.dto.ActivityLogQuery;
import com.sportify.identity.dto.ActivityLogResponse;
import org.springframework.data.domain.Page;

public interface ActivityLogQueries {
    Page<ActivityLogResponse> search(ActivityLogQuery filter);
}
