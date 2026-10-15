package com.sportify.identity.service;

import com.sportify.core.common.PagingDefaults;
import com.sportify.core.exception.BusinessRuleException;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MemberSearchPaging {
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "id", "m.id", "userId", "m.id", "memberCode", "m.memberCode",
            "fullName", "u.fullName", "phone", "u.phone", "email", "u.email", "status", "u.status");

    private MemberSearchPaging() {
    }

    public static Pageable bounded(Pageable pageable) {
        Map<String, Sort.Order> orders = new LinkedHashMap<>();
        for (Sort.Order order : pageable.getSort()) {
            String property = SORT_FIELDS.get(order.getProperty());
            if (property == null) {
                throw new BusinessRuleException("Unsupported member sort field: " + order.getProperty());
            }
            orders.putIfAbsent(property, new Sort.Order(order.getDirection(), property));
        }
        orders.putIfAbsent("m.id", Sort.Order.asc("m.id"));
        int page = pageable.isPaged() ? pageable.getPageNumber() : 0;
        int size = pageable.isPaged()
                ? Math.min(pageable.getPageSize(), PagingDefaults.MAX_PAGE_SIZE) : PagingDefaults.DEFAULT_PAGE_SIZE;
        return PageRequest.of(page, size, Sort.by(orders.values().stream().toList()));
    }
}
