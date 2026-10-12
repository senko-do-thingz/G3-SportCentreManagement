package com.sportify.payment.service;

import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.dto.PaymentResponse;
import com.sportify.payment.dto.UpdatePaymentMethodRequest;

public interface PaymentService {

    PaymentResponse getPayment(Long id, UserAccount actor);

    PaymentResponse updatePaymentMethod(Long id, UpdatePaymentMethodRequest request, UserAccount actor);
}
