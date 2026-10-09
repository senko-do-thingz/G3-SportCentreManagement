package com.sportify.catalog.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardTierResponse {
    private Long id;
    private String code;
    private String name;
    private BigDecimal price;
    private Integer durationMonths;
    private Integer discountPercentage;
    private String description;
    private Boolean isActive;
}
