package com.sportify.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembershipCardTierUpdateRequest {

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00", message = "Price must be greater than or equal to zero")
    @Digits(integer = 12, fraction = 2, message = "Price format is invalid")
    private BigDecimal price;

    @NotNull(message = "Duration in months is required")
    @Min(value = 0, message = "Duration months must be non-negative")
    private Integer durationMonths;

    @NotNull(message = "Discount percentage is required")
    @Min(value = 0, message = "Discount percentage cannot be less than 0")
    @Max(value = 100, message = "Discount percentage cannot be greater than 100")
    private Integer discountPercentage;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @NotNull(message = "Active flag is required")
    private Boolean active;
}
