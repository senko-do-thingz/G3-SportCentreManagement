package com.sportify.catalog.service;

import com.sportify.core.common.PagingDefaults;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class FrontDeskQueuePaging {
    private FrontDeskQueuePaging() {
    }

    public static Pageable bounded(Pageable pageable) {
        int page = pageable.isPaged() ? pageable.getPageNumber() : 0;
        int size = pageable.isPaged()
                ? Math.min(pageable.getPageSize(), PagingDefaults.MAX_PAGE_SIZE) : PagingDefaults.DEFAULT_PAGE_SIZE;
        return PageRequest.of(page, size, Sort.by("createdAt", "id"));
    }
}
