package com.sportify.catalog.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrontDeskQueuePagingTest {
    @Test
    void bounded_whenUnpaged_usesOldestFirstPageOfTwentyRows() {
        assertEquals(PageRequest.of(0, 20, Sort.by("createdAt", "id")),
                FrontDeskQueuePaging.bounded(Pageable.unpaged()));
    }

    @Test
    void bounded_whenClientSortsOrRequestsTooManyRows_forcesQueueOrderAndCapsSize() {
        assertEquals(PageRequest.of(2, 100, Sort.by("createdAt", "id")),
                FrontDeskQueuePaging.bounded(PageRequest.of(2, 500, Sort.by("passwordHash"))));
    }
}
