package com.sportify.payment.service.impl;

import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.payment.entity.Invoice;
import com.sportify.payment.entity.InvoiceStatus;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.InvoiceRepository;
import com.sportify.payment.repository.PaymentRepository;
import com.sportify.payment.service.PaymentRefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PaymentRefundServiceImpl implements PaymentRefundService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Payment markRefunded(Long paymentId, String refundCode, BigDecimal amountRefunded) {
        if (paymentId == null) {
            throw new BusinessRuleException("Payment ID cannot be null");
        }
        if (amountRefunded != null && amountRefunded.signum() <= 0) {
            throw new BusinessRuleException("Amount refunded must be greater than zero");
        }
        // Returns the row the caller already locked (same transaction, same persistence context).
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for id: " + paymentId));

        if (payment.getPaymentStatus() == PaymentStatus.REFUNDED) {
            throw new ConflictException("Payment " + payment.getPaymentCode() + " has already been refunded",
                    CODE_PAYMENT_ALREADY_REFUNDED);
        }
        if (payment.getPaymentStatus() != PaymentStatus.SUCCESS) {
            throw new ConflictException("Payment " + payment.getPaymentCode() + " cannot be refunded: it is "
                    + payment.getPaymentStatus() + ", not SUCCESS", CODE_PAYMENT_NOT_REFUNDABLE);
        }

        payment.setPaymentStatus(PaymentStatus.REFUNDED);
        payment.setNotes(PaymentNotes.append(payment.getNotes(), "Refunded by " + refundCode
                + (amountRefunded != null ? ": " + amountRefunded.toPlainString() : "")));

        for (Invoice invoice : invoiceRepository.findByPaymentId(payment.getId())) {
            if (invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.ISSUED) {
                invoice.setStatus(InvoiceStatus.REFUNDED);
                invoiceRepository.save(invoice);
            }
        }
        return paymentRepository.save(payment);
    }
}
