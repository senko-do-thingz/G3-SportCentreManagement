package com.sportify.payment.repository;

import com.sportify.payment.entity.Invoice;
import com.sportify.payment.entity.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    List<Invoice> findByPaymentId(Long paymentId);

    List<Invoice> findByMemberIdOrderByIssueDateDesc(Long memberId);

    List<Invoice> findByStatus(InvoiceStatus status);

    @Query(value = "SELECT NEXT VALUE FOR seq_invoice_number", nativeQuery = true)
    Long getNextInvoiceNumberSequence();
}
