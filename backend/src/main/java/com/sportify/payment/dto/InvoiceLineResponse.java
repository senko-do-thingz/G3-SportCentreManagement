package com.sportify.payment.dto;

import com.sportify.payment.entity.InvoiceItemType;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceLineResponse {

    private Long id;
    private InvoiceItemType itemType;
    private Long itemReferenceId;
    private String description;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
}
