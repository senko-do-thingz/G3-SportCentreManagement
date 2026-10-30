package com.sportify.payment.service.impl;

import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.dto.InvoiceLineResponse;
import com.sportify.payment.dto.InvoiceResponse;
import com.sportify.payment.entity.Invoice;
import com.sportify.payment.entity.InvoiceLine;
import com.sportify.payment.entity.Payment;

import java.util.Comparator;
import java.util.List;

/**
 * Builds the receipt answer (InvoiceResponse) from an invoice and its payment. Must be called inside a
 * transaction, because the member, the user account and the lines are lazy associations.
 * Lines are returned in insert order (id ascending).
 */
final class InvoiceResponseMapper {

    private InvoiceResponseMapper() {
    }

    static InvoiceResponse toResponse(Invoice invoice) {
        Payment payment = invoice.getPayment();
        MemberProfile member = invoice.getMember();
        UserAccount account = member != null ? member.getUserAccount() : null;
        List<InvoiceLineResponse> lines = invoice.getLines().stream()
                .sorted(Comparator.comparing(InvoiceLine::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(InvoiceResponseMapper::toLineResponse)
                .toList();
        return InvoiceResponse.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .status(invoice.getStatus())
                .issueDate(invoice.getIssueDate())
                .paymentId(payment != null ? payment.getId() : null)
                .paymentCode(payment != null ? payment.getPaymentCode() : null)
                .paymentMethod(payment != null ? payment.getPaymentMethod() : null)
                .paymentReference(payment != null ? payment.getReferenceCode() : null)
                .paymentTime(payment != null ? payment.getPaymentTime() : null)
                .settlementType(payment != null ? payment.getSettlementType() : null)
                .settlementProvider(payment != null ? payment.getSettlementProvider() : null)
                .simulatedTransactionId(payment != null ? payment.getSimulatedTransactionId() : null)
                .memberId(member != null ? member.getId() : null)
                .memberCode(member != null ? member.getMemberCode() : null)
                .memberFullName(account != null ? account.getFullName() : null)
                .subtotalAmount(invoice.getSubtotalAmount())
                .discountAmount(invoice.getDiscountAmount())
                .taxAmount(invoice.getTaxAmount())
                .totalAmount(invoice.getTotalAmount())
                .lines(lines)
                .build();
    }

    private static InvoiceLineResponse toLineResponse(InvoiceLine line) {
        return InvoiceLineResponse.builder()
                .id(line.getId())
                .itemType(line.getItemType())
                .itemReferenceId(line.getItemReferenceId())
                .description(line.getDescription())
                .quantity(line.getQuantity())
                .unitPrice(line.getUnitPrice())
                .lineTotal(line.getLineTotal())
                .build();
    }
}
