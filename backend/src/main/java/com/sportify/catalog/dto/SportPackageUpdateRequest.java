package com.sportify.catalog.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SportPackageUpdateRequest {

    @NotBlank(message = "Package name is required")
    private String name;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    @JsonAlias({"price", "priceAmount"})
    private BigDecimal price;

    @NotNull(message = "Session count is required")
    @Min(value = 1, message = "Session count must be at least 1")
    private Integer sessionCount;

    @NotNull(message = "Duration in days is required")
    @Min(value = 1, message = "Duration must be at least 1 day")
    private Integer durationDays;

    @NotNull(message = "Session minutes is required")
    private Integer sessionMinutes;

    @NotNull(message = "Active flag is required")
    @JsonAlias({"active", "isActive"})
    private Boolean active;

    private String description;

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
