package com.sportify.catalog.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** A new paid tier, independent of issuing a card to any member. */
public record MembershipCardTierCreateRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,29}") String code,
        @NotBlank @Size(max = 50) String name,
        @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal price,
        @NotNull @Min(1) @Max(120) Integer durationMonths,
        @NotNull @Min(0) @Max(100) Integer discountPercentage,
        @Size(max = 500) String description,
        @NotNull @Min(2) @Max(1000000) Integer tierRank,
        @NotNull Boolean active) {}
