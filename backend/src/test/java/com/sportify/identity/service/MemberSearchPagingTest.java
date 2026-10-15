package com.sportify.identity.service;

import com.sportify.core.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemberSearchPagingTest {
    @Test
    void bounded_whenIdAndUserIdAreRequested_keepsOnlyFirstMappedOrder() {
        var result = MemberSearchPaging.bounded(PageRequest.of(0, 20,
                Sort.by(Sort.Order.desc("userId"), Sort.Order.asc("id"))));
        assertEquals(List.of(Sort.Order.desc("m.id")), result.getSort().stream().toList());
    }

    @Test
    void bounded_whenFieldIsRepeated_keepsFirstDirectionAndAppendsId() {
        var result = MemberSearchPaging.bounded(PageRequest.of(0, 20,
                Sort.by(Sort.Order.desc("fullName"), Sort.Order.asc("fullName"))));
        assertEquals(List.of(Sort.Order.desc("u.fullName"), Sort.Order.asc("m.id")),
                result.getSort().stream().toList());
    }

    @Test
    void bounded_whenUnpaged_usesFirstPageOfTwentyRows() {
        var result = MemberSearchPaging.bounded(Pageable.unpaged());
        assertEquals(PageRequest.of(0, 20, Sort.by("m.id")), result);
    }

    @Test
    void bounded_whenSortedUnpaged_preservesAllowedSortAndUsesDefaultPage() {
        var result = MemberSearchPaging.bounded(Pageable.unpaged(Sort.by("memberCode")));
        assertEquals(PageRequest.of(0, 20, Sort.by("m.memberCode", "m.id")), result);
    }

    @Test
    void bounded_whenUnknownFieldFollowsKnownField_throwsBeforeRepositoryAccess() {
        assertThrows(BusinessRuleException.class, () -> MemberSearchPaging.bounded(
                PageRequest.of(0, 20, Sort.by("id", "passwordHash"))));
    }

    @Test
    void bounded_whenPageSizeExceedsCap_keepsPageAndLimitsSize() {
        assertEquals(PageRequest.of(3, 100, Sort.by("m.id")),
                MemberSearchPaging.bounded(PageRequest.of(3, 500)));
    }
}
