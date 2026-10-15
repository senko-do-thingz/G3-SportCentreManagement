package com.sportify.core.common;

import com.sportify.core.exception.BusinessRuleException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;

public final class FrontDeskPaging {
    private static final Map<String, String> SEARCH_SORTS = Map.of(
            "id", "m.id", "userId", "m.id", "memberCode", "m.memberCode",
            "fullName", "u.fullName", "phone", "u.phone", "email", "u.email", "status", "u.status");

    private FrontDeskPaging() {
    }

    public static Pageable search(Pageable pageable) {
        Sort sort = pageable.getSort().isUnsorted() ? Sort.by("id") : pageable.getSort();
        Sort mapped = Sort.by(sort.stream().map(order -> {
            String property = SEARCH_SORTS.get(order.getProperty());
            if (property == null) {
                throw new BusinessRuleException("Unsupported member sort field: " + order.getProperty());
            }
            return new Sort.Order(order.getDirection(), property);
        }).toList());
        if (mapped.getOrderFor("m.id") == null) {
            mapped = mapped.and(Sort.by("m.id"));
        }
        return PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), mapped);
    }

    public static Pageable queue(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100),
                Sort.by("createdAt", "id"));
    }
}
