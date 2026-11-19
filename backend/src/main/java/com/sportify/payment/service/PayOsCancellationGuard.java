package com.sportify.payment.service;

import com.sportify.core.exception.ConflictException;
import com.sportify.payment.config.PayOsProperties;
import com.sportify.payment.entity.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PayOsCancellationGuard implements PaymentCancellationGuard {
    private final PayOsApiClient api;
    private final PayOsProperties properties;

    @Override
    public String blockedReason(Payment payment) {
        return null; // Unpaid links may be cancelled; the provider is closed before local cancellation commits.
    }

    @Override
    public void onCancelled(Payment payment) {
        String linkId = payment.getPayOsPaymentLinkId();
        if (linkId == null || linkId.isBlank()) return;
        if (!properties.isEnabled()) {
            throw new ConflictException("PayOS must be enabled to close this payment link safely.");
        }
        PayOsApiClient.PaymentLinkData result = api.cancelPaymentLink(linkId);
        if (result.status() != null && "PAID".equalsIgnoreCase(result.status())) {
            throw new ConflictException("PayOS already received this payment. Wait for its webhook to update the status.");
        }
    }
}
