package com.sportify.payment.repository;

import com.sportify.payment.entity.InvoiceItemType;
import com.sportify.payment.entity.InvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, Long> {

    List<InvoiceLine> findByInvoiceId(Long invoiceId);

    List<InvoiceLine> findByItemTypeAndItemReferenceId(InvoiceItemType itemType, Long itemReferenceId);

    /**
     * Number of invoice lines of a payment that bill something other than the given item. Zero means every invoice
     * line of the payment bills that item (or the payment has no invoice yet).
     */
    @Query("SELECT COUNT(l) FROM InvoiceLine l WHERE l.invoice.payment.id = :paymentId "
            + "AND NOT (l.itemType = :itemType AND l.itemReferenceId = :itemReferenceId)")
    long countPaymentLinesForOtherItems(@Param("paymentId") Long paymentId,
                                        @Param("itemType") InvoiceItemType itemType,
                                        @Param("itemReferenceId") Long itemReferenceId);
}
