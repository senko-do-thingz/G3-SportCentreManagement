package com.sportify.catalog.service;

import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;

import java.math.BigDecimal;
import java.util.Map;

/** Validates the expectation against the price actually used by the enclosing sale transaction. */
public final class DeskSaleCharge {
    public static final String PRICE_CHANGED = "DESK_SALE_PRICE_CHANGED";

    private DeskSaleCharge() {
    }

    public static void verify(BigDecimal expected, BigDecimal actual, BigDecimal received, PaymentMethod method) {
        if (expected == null) return;
        if (expected.signum() < 0 || expected.stripTrailingZeros().scale() > 2
                || expected.toBigInteger().toString().length() > 10) {
            throw new BusinessRuleException("Expected amount due must be nonnegative with at most 10 integer digits and 2 decimals");
        }
        if (expected.compareTo(actual) != 0) {
            throw new ConflictException("The amount due has changed. Refresh the price and record the actual amount received before retrying.",
                    PRICE_CHANGED, Map.of("expectedAmountDue", expected, "currentAmountDue", actual));
        }
        if (actual.signum() > 0 && (received == null || received.signum() <= 0 || method == null)) {
            throw new BusinessRuleException("A paid desk sale with an expected amount requires a payment method and the actual amount received");
        }
        if (actual.signum() == 0 && received != null) {
            throw new BusinessRuleException("Do not record an amount received for a free desk sale");
        }
    }

    /** Response-only collection evidence from the validated sale; free sales never record collection or change. */
    public static Collection collection(Payment payment, BigDecimal received) {
        if (payment == null) return new Collection(BigDecimal.ZERO, BigDecimal.ZERO);
        BigDecimal recorded = received != null ? received : payment.getAmount();
        BigDecimal change = payment.getPaymentMethod() == PaymentMethod.CASH
                ? recorded.subtract(payment.getAmount()).max(BigDecimal.ZERO) : BigDecimal.ZERO;
        return new Collection(recorded, change);
    }

    public record Collection(BigDecimal amountReceived, BigDecimal changeAmount) {
    }
}
