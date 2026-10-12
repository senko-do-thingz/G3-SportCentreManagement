package com.sportify.payment.service;

import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.dto.PaymentResponse;
import com.sportify.payment.dto.UpdatePaymentMethodRequest;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;

import java.math.BigDecimal;
import java.util.Set;

public interface PaymentService {

    Set<PaymentMethod> ONLINE_ALLOWED_PAYMENT_METHODS = Set.of(
            PaymentMethod.BANK_TRANSFER,
            PaymentMethod.MOMO,
            PaymentMethod.VNPAY,
            PaymentMethod.ZALOPAY
    );

    Payment createPendingPayment(MemberProfile member, BigDecimal amount, PaymentMethod method);

    Payment createPendingPayment(UserAccount memberUser, BigDecimal amount, PaymentMethod method);

    Payment markPaymentSuccess(Long paymentId, UserAccount confirmedBy);

    Payment markPaymentFailed(Long paymentId, String reason);

    PaymentResponse getPayment(Long id, UserAccount actor);

    PaymentResponse updatePaymentMethod(Long id, UpdatePaymentMethodRequest request, UserAccount actor);
}
