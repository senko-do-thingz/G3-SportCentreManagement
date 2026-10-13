package com.sportify.catalog.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembershipCardTierUpdateRequest {

    private String name;

    @NotNull(message = "Price is required")
    @JsonAlias({"price", "priceAmount"})
    private BigDecimal price;

    @NotNull(message = "Duration in months is required")
    @Min(value = 0, message = "Duration months must be non-negative")
    private Integer durationMonths;

    @NotNull(message = "Discount percentage is required")
    @Min(value = 0, message = "Discount percentage cannot be less than 0")
    @Max(value = 100, message = "Discount percentage cannot be greater than 100")
    private Integer discountPercentage;

    private String description;

    @NotNull(message = "Active flag is required")
    @JsonAlias({"active", "isActive"})
    private Boolean active;

    public Boolean getIsActive() {
        return active;
    }

    public void setIsActive(Boolean active) {
        this.active = active;
    }

    public BigDecimal getPriceAmount() {
        return price;
    }

    public void setPriceAmount(BigDecimal priceAmount) {
        this.price = priceAmount;
    }
}
