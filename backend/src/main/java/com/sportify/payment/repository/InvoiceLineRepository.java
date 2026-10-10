package com.sportify.payment.repository;

import com.sportify.payment.entity.InvoiceItemType;
import com.sportify.payment.entity.InvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, Long> {

    List<InvoiceLine> findByInvoiceId(Long invoiceId);

    List<InvoiceLine> findByItemTypeAndItemReferenceId(InvoiceItemType itemType, Long itemReferenceId);
}
