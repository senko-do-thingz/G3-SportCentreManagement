package com.sportify.catalog.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable JSON shape for paginated results (serializing Spring's PageImpl directly is not a stable contract).
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
