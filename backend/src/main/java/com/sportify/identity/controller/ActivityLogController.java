package com.sportify.identity.controller;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.identity.dto.ActivityLogResponse;
import com.sportify.identity.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/manager/activity-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class ActivityLogController {
    private final ActivityLogService activityLogService;

    @GetMapping
    public ResponseEntity<PageResponse<ActivityLogResponse>> list(
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(activityLogService.list(actorId, action, from, to, search, page, size));
    }
}
