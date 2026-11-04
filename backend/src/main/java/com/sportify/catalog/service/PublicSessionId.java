package com.sportify.catalog.service;

import com.sportify.core.exception.BusinessRuleException;
import java.time.DateTimeException;
import java.time.LocalDate;

/** A canonical business ID, never a booking, class or numeric session primary key. */
public record PublicSessionId(String value) {
    public PublicSessionId {
        if (value == null || !value.matches("[0-9]{6}(FB|SW|BM|VB|BK|TE)(0[1-9]|[1-9][0-9])"))
            throw new BusinessRuleException("Session ID must use ddMMyy, an approved sport code and allocation 01-99");
        try {
            LocalDate.of(2000 + Integer.parseInt(value.substring(4,6)),
                    Integer.parseInt(value.substring(2,4)), Integer.parseInt(value.substring(0,2)));
        } catch (DateTimeException invalid) {
            throw new BusinessRuleException("Session ID contains an invalid scheduled date");
        }
    }
}
