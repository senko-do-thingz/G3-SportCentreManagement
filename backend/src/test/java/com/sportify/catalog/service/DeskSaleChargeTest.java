package com.sportify.catalog.service;

import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DeskSaleChargeTest {
    @ParameterizedTest
    @CsvSource({"0,1", "10,11", "10,9", "10,0", "100.01,100.02"})
    void anyChangedExpectedChargeConflictsBeforeCollection(String expected, String actual) {
        var failure = assertThrows(ConflictException.class, () -> DeskSaleCharge.verify(
                new BigDecimal(expected), new BigDecimal(actual), null, null));
        assertEquals("DESK_SALE_PRICE_CHANGED", failure.getCode());
        assertEquals(new BigDecimal(actual), failure.getDetails().get("currentAmountDue"));
    }

    @Test
    void absentExpectationPreservesLegacyDefaultsAndExplicitZeroStaysFree() {
        assertDoesNotThrow(() -> DeskSaleCharge.verify(null, BigDecimal.TEN, null, null));
        assertDoesNotThrow(() -> DeskSaleCharge.verify(new BigDecimal("0.00"), BigDecimal.ZERO, null, null));
        assertEquals(new DeskSaleCharge.Collection(BigDecimal.ZERO, BigDecimal.ZERO),
                DeskSaleCharge.collection(null, new BigDecimal("999999")));
    }

    @Test
    void explicitPaidQuoteRequiresActualCollectionAndMethod() {
        assertThrows(BusinessRuleException.class, () -> DeskSaleCharge.verify(BigDecimal.TEN, BigDecimal.TEN, null, PaymentMethod.CASH));
        assertThrows(BusinessRuleException.class, () -> DeskSaleCharge.verify(BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, null));
        assertThrows(BusinessRuleException.class, () -> DeskSaleCharge.verify(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, PaymentMethod.CASH));
        assertDoesNotThrow(() -> DeskSaleCharge.verify(BigDecimal.TEN, BigDecimal.TEN, new BigDecimal("20"), PaymentMethod.CASH));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0.001", "10000000000"})
    void serviceRejectsInvalidExpectationPrecisionAndRange(String expected) {
        assertThrows(BusinessRuleException.class, () -> DeskSaleCharge.verify(new BigDecimal(expected), BigDecimal.ZERO, null, null));
    }

    @Test
    void collectionTotalsUseTheRecordedPaymentAndPreserveCashChangePrecision() {
        var cash = Payment.builder().amount(new BigDecimal("100.25")).paymentMethod(PaymentMethod.CASH).build();
        assertEquals(new DeskSaleCharge.Collection(new BigDecimal("110.50"), new BigDecimal("10.25")),
                DeskSaleCharge.collection(cash, new BigDecimal("110.50")));
        assertEquals(new BigDecimal("0.00"), DeskSaleCharge.collection(cash, null).changeAmount());
        cash.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        assertEquals(BigDecimal.ZERO, DeskSaleCharge.collection(cash, new BigDecimal("100.25")).changeAmount());
    }
}
