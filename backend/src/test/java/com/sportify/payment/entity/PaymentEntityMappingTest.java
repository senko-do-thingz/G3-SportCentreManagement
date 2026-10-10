package com.sportify.payment.entity;

import com.sportify.catalog.entity.MemberCard;
import com.sportify.catalog.entity.RefundRequest;
import com.sportify.catalog.entity.SportPackageRegistration;
import jakarta.persistence.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentEntityMappingTest {

    @Test
    @DisplayName("Payment entity metadata and column annotations must match V14 table")
    void testPaymentEntityMapping() throws Exception {
        Table table = Payment.class.getAnnotation(Table.class);
        assertThat(table).isNotNull();
        assertThat(table.name()).isEqualTo("payment");

        Field id = Payment.class.getDeclaredField("id");
        assertThat(id.getAnnotation(Id.class)).isNotNull();
        assertThat(id.getAnnotation(GeneratedValue.class).strategy()).isEqualTo(GenerationType.IDENTITY);

        Field paymentCode = Payment.class.getDeclaredField("paymentCode");
        Column codeCol = paymentCode.getAnnotation(Column.class);
        assertThat(codeCol.name()).isEqualTo("payment_code");
        assertThat(codeCol.nullable()).isFalse();
        assertThat(codeCol.unique()).isTrue();
        assertThat(codeCol.length()).isEqualTo(30);

        Field member = Payment.class.getDeclaredField("member");
        ManyToOne memberMto = member.getAnnotation(ManyToOne.class);
        assertThat(memberMto.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(memberMto.optional()).isFalse();
        JoinColumn memberJc = member.getAnnotation(JoinColumn.class);
        assertThat(memberJc.name()).isEqualTo("member_id");
        assertThat(memberJc.nullable()).isFalse();

        Field amount = Payment.class.getDeclaredField("amount");
        Column amountCol = amount.getAnnotation(Column.class);
        assertThat(amountCol.name()).isEqualTo("amount");
        assertThat(amountCol.nullable()).isFalse();
        assertThat(amountCol.precision()).isEqualTo(12);
        assertThat(amountCol.scale()).isEqualTo(2);

        Field method = Payment.class.getDeclaredField("paymentMethod");
        assertThat(method.getAnnotation(Enumerated.class).value()).isEqualTo(EnumType.STRING);
        Column methodCol = method.getAnnotation(Column.class);
        assertThat(methodCol.name()).isEqualTo("payment_method");
        assertThat(methodCol.nullable()).isFalse();
        assertThat(methodCol.length()).isEqualTo(30);

        Field status = Payment.class.getDeclaredField("paymentStatus");
        assertThat(status.getAnnotation(Enumerated.class).value()).isEqualTo(EnumType.STRING);
        Column statusCol = status.getAnnotation(Column.class);
        assertThat(statusCol.name()).isEqualTo("payment_status");
        assertThat(statusCol.nullable()).isFalse();
        assertThat(statusCol.length()).isEqualTo(20);

        Field refCode = Payment.class.getDeclaredField("referenceCode");
        Column refCol = refCode.getAnnotation(Column.class);
        assertThat(refCol.name()).isEqualTo("reference_code");
        assertThat(refCol.length()).isEqualTo(100);

        Field notes = Payment.class.getDeclaredField("notes");
        Column notesCol = notes.getAnnotation(Column.class);
        assertThat(notesCol.name()).isEqualTo("notes");
        assertThat(notesCol.length()).isEqualTo(500);

        Field paymentTime = Payment.class.getDeclaredField("paymentTime");
        Column ptimeCol = paymentTime.getAnnotation(Column.class);
        assertThat(ptimeCol.name()).isEqualTo("payment_time");
        assertThat(ptimeCol.nullable()).isFalse();

        Field version = Payment.class.getDeclaredField("version");
        assertThat(version.getAnnotation(Version.class)).isNotNull();
        Column verCol = version.getAnnotation(Column.class);
        assertThat(verCol.name()).isEqualTo("version");
        assertThat(verCol.nullable()).isFalse();

        Field createdAt = Payment.class.getDeclaredField("createdAt");
        Column createdCol = createdAt.getAnnotation(Column.class);
        assertThat(createdCol.name()).isEqualTo("created_at");
        assertThat(createdCol.nullable()).isFalse();

        Field updatedAt = Payment.class.getDeclaredField("updatedAt");
        Column updatedCol = updatedAt.getAnnotation(Column.class);
        assertThat(updatedCol.name()).isEqualTo("updated_at");
    }

    @Test
    @DisplayName("Invoice entity metadata and column annotations must match V14 table")
    void testInvoiceEntityMapping() throws Exception {
        Table table = Invoice.class.getAnnotation(Table.class);
        assertThat(table).isNotNull();
        assertThat(table.name()).isEqualTo("invoice");

        Field invoiceNumber = Invoice.class.getDeclaredField("invoiceNumber");
        Column numCol = invoiceNumber.getAnnotation(Column.class);
        assertThat(numCol.name()).isEqualTo("invoice_number");
        assertThat(numCol.nullable()).isFalse();
        assertThat(numCol.unique()).isTrue();
        assertThat(numCol.length()).isEqualTo(30);

        Field payment = Invoice.class.getDeclaredField("payment");
        ManyToOne payMto = payment.getAnnotation(ManyToOne.class);
        assertThat(payMto.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(payMto.optional()).isFalse();
        JoinColumn payJc = payment.getAnnotation(JoinColumn.class);
        assertThat(payJc.name()).isEqualTo("payment_id");
        assertThat(payJc.nullable()).isFalse();

        Field member = Invoice.class.getDeclaredField("member");
        ManyToOne memMto = member.getAnnotation(ManyToOne.class);
        assertThat(memMto.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(memMto.optional()).isFalse();
        JoinColumn memJc = member.getAnnotation(JoinColumn.class);
        assertThat(memJc.name()).isEqualTo("member_id");
        assertThat(memJc.nullable()).isFalse();

        Field subtotal = Invoice.class.getDeclaredField("subtotalAmount");
        Column subCol = subtotal.getAnnotation(Column.class);
        assertThat(subCol.name()).isEqualTo("subtotal_amount");
        assertThat(subCol.nullable()).isFalse();
        assertThat(subCol.precision()).isEqualTo(12);
        assertThat(subCol.scale()).isEqualTo(2);

        Field discount = Invoice.class.getDeclaredField("discountAmount");
        Column discCol = discount.getAnnotation(Column.class);
        assertThat(discCol.name()).isEqualTo("discount_amount");
        assertThat(discCol.nullable()).isFalse();
        assertThat(discCol.precision()).isEqualTo(12);
        assertThat(discCol.scale()).isEqualTo(2);

        Field tax = Invoice.class.getDeclaredField("taxAmount");
        Column taxCol = tax.getAnnotation(Column.class);
        assertThat(taxCol.name()).isEqualTo("tax_amount");
        assertThat(taxCol.nullable()).isFalse();
        assertThat(taxCol.precision()).isEqualTo(12);
        assertThat(taxCol.scale()).isEqualTo(2);

        Field total = Invoice.class.getDeclaredField("totalAmount");
        Column totalCol = total.getAnnotation(Column.class);
        assertThat(totalCol.name()).isEqualTo("total_amount");
        assertThat(totalCol.nullable()).isFalse();
        assertThat(totalCol.precision()).isEqualTo(12);
        assertThat(totalCol.scale()).isEqualTo(2);

        Field status = Invoice.class.getDeclaredField("status");
        assertThat(status.getAnnotation(Enumerated.class).value()).isEqualTo(EnumType.STRING);
        Column statusCol = status.getAnnotation(Column.class);
        assertThat(statusCol.name()).isEqualTo("status");
        assertThat(statusCol.nullable()).isFalse();
        assertThat(statusCol.length()).isEqualTo(20);

        Field issueDate = Invoice.class.getDeclaredField("issueDate");
        Column issueCol = issueDate.getAnnotation(Column.class);
        assertThat(issueCol.name()).isEqualTo("issue_date");
        assertThat(issueCol.nullable()).isFalse();

        Field lines = Invoice.class.getDeclaredField("lines");
        OneToMany otm = lines.getAnnotation(OneToMany.class);
        assertThat(otm.mappedBy()).isEqualTo("invoice");
        assertThat(otm.cascade()).contains(CascadeType.ALL);
        assertThat(otm.orphanRemoval()).isTrue();
    }

    @Test
    @DisplayName("InvoiceLine entity metadata and column annotations must match V14 table")
    void testInvoiceLineEntityMapping() throws Exception {
        Table table = InvoiceLine.class.getAnnotation(Table.class);
        assertThat(table).isNotNull();
        assertThat(table.name()).isEqualTo("invoice_line");

        Field invoice = InvoiceLine.class.getDeclaredField("invoice");
        ManyToOne invMto = invoice.getAnnotation(ManyToOne.class);
        assertThat(invMto.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(invMto.optional()).isFalse();
        JoinColumn invJc = invoice.getAnnotation(JoinColumn.class);
        assertThat(invJc.name()).isEqualTo("invoice_id");
        assertThat(invJc.nullable()).isFalse();

        Field itemType = InvoiceLine.class.getDeclaredField("itemType");
        assertThat(itemType.getAnnotation(Enumerated.class).value()).isEqualTo(EnumType.STRING);
        Column typeCol = itemType.getAnnotation(Column.class);
        assertThat(typeCol.name()).isEqualTo("item_type");
        assertThat(typeCol.nullable()).isFalse();
        assertThat(typeCol.length()).isEqualTo(30);

        Field refId = InvoiceLine.class.getDeclaredField("itemReferenceId");
        Column refCol = refId.getAnnotation(Column.class);
        assertThat(refCol.name()).isEqualTo("item_reference_id");
        assertThat(refCol.nullable()).isFalse();

        Field desc = InvoiceLine.class.getDeclaredField("description");
        Column descCol = desc.getAnnotation(Column.class);
        assertThat(descCol.name()).isEqualTo("description");
        assertThat(descCol.nullable()).isFalse();
        assertThat(descCol.length()).isEqualTo(255);

        Field qty = InvoiceLine.class.getDeclaredField("quantity");
        Column qtyCol = qty.getAnnotation(Column.class);
        assertThat(qtyCol.name()).isEqualTo("quantity");
        assertThat(qtyCol.nullable()).isFalse();

        Field unitPrice = InvoiceLine.class.getDeclaredField("unitPrice");
        Column unitCol = unitPrice.getAnnotation(Column.class);
        assertThat(unitCol.name()).isEqualTo("unit_price");
        assertThat(unitCol.nullable()).isFalse();
        assertThat(unitCol.precision()).isEqualTo(12);
        assertThat(unitCol.scale()).isEqualTo(2);

        Field lineTotal = InvoiceLine.class.getDeclaredField("lineTotal");
        Column lineCol = lineTotal.getAnnotation(Column.class);
        assertThat(lineCol.name()).isEqualTo("line_total");
        assertThat(lineCol.nullable()).isFalse();
        assertThat(lineCol.precision()).isEqualTo(12);
        assertThat(lineCol.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("payment_id foreign key mappings on MemberCard, SportPackageRegistration, and RefundRequest")
    void testPaymentForeignKeysOnExistingEntities() throws Exception {
        // MemberCard.payment
        Field mcPayment = MemberCard.class.getDeclaredField("payment");
        ManyToOne mcMto = mcPayment.getAnnotation(ManyToOne.class);
        assertThat(mcMto.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(mcMto.optional()).isTrue();
        JoinColumn mcJc = mcPayment.getAnnotation(JoinColumn.class);
        assertThat(mcJc.name()).isEqualTo("payment_id");

        // SportPackageRegistration.payment
        Field sprPayment = SportPackageRegistration.class.getDeclaredField("payment");
        ManyToOne sprMto = sprPayment.getAnnotation(ManyToOne.class);
        assertThat(sprMto.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(sprMto.optional()).isTrue();
        JoinColumn sprJc = sprPayment.getAnnotation(JoinColumn.class);
        assertThat(sprJc.name()).isEqualTo("payment_id");

        // RefundRequest.payment
        Field rrPayment = RefundRequest.class.getDeclaredField("payment");
        ManyToOne rrMto = rrPayment.getAnnotation(ManyToOne.class);
        assertThat(rrMto.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(rrMto.optional()).isTrue();
        JoinColumn rrJc = rrPayment.getAnnotation(JoinColumn.class);
        assertThat(rrJc.name()).isEqualTo("payment_id");
    }

    @Test
    @DisplayName("Enums must contain all required constants matching V14 check constraints")
    void testEnumConstants() {
        assertThat(PaymentMethod.values()).containsExactly(
                PaymentMethod.CASH,
                PaymentMethod.CREDIT_CARD,
                PaymentMethod.BANK_TRANSFER,
                PaymentMethod.MOMO,
                PaymentMethod.VNPAY,
                PaymentMethod.ZALOPAY
        );

        assertThat(PaymentStatus.values()).containsExactly(
                PaymentStatus.PENDING,
                PaymentStatus.SUCCESS,
                PaymentStatus.FAILED,
                PaymentStatus.REFUNDED
        );

        assertThat(InvoiceStatus.values()).containsExactly(
                InvoiceStatus.ISSUED,
                InvoiceStatus.PAID,
                InvoiceStatus.CANCELLED,
                InvoiceStatus.REFUNDED
        );

        assertThat(InvoiceItemType.values()).containsExactly(
                InvoiceItemType.SPORT_PACKAGE,
                InvoiceItemType.MEMBERSHIP_CARD,
                InvoiceItemType.CLASS_DROP_IN,
                InvoiceItemType.PENALTY_FEE
        );
    }

    @Test
    @DisplayName("Lifecycle methods should initialize defaults properly")
    void testLifecycleDefaults() {
        Payment payment = Payment.builder().build();
        payment.prePersist();
        assertThat(payment.getPaymentTime()).isNotNull();
        assertThat(payment.getCreatedAt()).isNotNull();
        assertThat(payment.getUpdatedAt()).isNotNull();

        LocalDateTime oldPaymentTime = LocalDateTime.of(2020, 1, 1, 0, 0, 0);
        payment.setUpdatedAt(oldPaymentTime);
        payment.preUpdate();
        assertThat(payment.getUpdatedAt()).isAfter(oldPaymentTime);

        Invoice invoice = Invoice.builder().build();
        invoice.prePersist();
        assertThat(invoice.getIssueDate()).isNotNull();
        assertThat(invoice.getCreatedAt()).isNotNull();
        assertThat(invoice.getUpdatedAt()).isNotNull();
        assertThat(invoice.getDiscountAmount()).isEqualTo(BigDecimal.ZERO);
        assertThat(invoice.getTaxAmount()).isEqualTo(BigDecimal.ZERO);

        LocalDateTime oldInvoiceTime = LocalDateTime.of(2020, 1, 1, 0, 0, 0);
        invoice.setUpdatedAt(oldInvoiceTime);
        invoice.preUpdate();
        assertThat(invoice.getUpdatedAt()).isAfter(oldInvoiceTime);

        InvoiceLine line = InvoiceLine.builder().build();
        line.prePersist();
        assertThat(line.getCreatedAt()).isNotNull();
        assertThat(line.getQuantity()).isEqualTo(1);

        invoice.addLine(line);
        assertThat(invoice.getLines()).contains(line);
        assertThat(line.getInvoice()).isEqualTo(invoice);
    }
}
