package com.sportify.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SportPackageUpdateRequest {

    @NotBlank(message = "Package name is required")
    @Size(max = 100, message = "Package name must not exceed 100 characters")
    private String name;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    @Digits(integer = 12, fraction = 2, message = "Price format is invalid")
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
    private Boolean active;
}
