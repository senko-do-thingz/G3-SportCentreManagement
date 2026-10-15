package com.sportify.catalog.dto;

import com.sportify.catalog.entity.CardStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberCardResponse {
    private Long id;
    private String cardCode;
    private Long memberId;
    private String memberCode;
    private String memberFullName;
    private Long tierId;
    private String tierCode;
    private String tierName;
    private Integer tierRank;
    private boolean upgradeRequest;
    private Long replacesCardId;
    private Integer discountPercentage;
    private LocalDate startDate;
    private LocalDate endDate;
    private CardStatus status;
    private BigDecimal pricePaid;
    private Long paymentId;
    private String paymentCode;
    /** Invoice of the payment, set when the response comes from a sale or an activation that issued it (BE-19). */
    private Long invoiceId;
    private String invoiceNumber;
    private LocalDateTime createdAt;
}
