package com.sportify.catalog.dto;

import com.sportify.catalog.entity.TrainingFormat;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SportPackageCreateRequest {

    @NotBlank(message = "Package code is required")
    private String code;

    @NotBlank(message = "Package name is required")
    private String name;

    @NotNull(message = "Sport ID is required")
    private Long sportId;

    @NotNull(message = "Training format is required")
    private TrainingFormat trainingFormat;

    @NotNull(message = "Duration in days is required")
    @Min(value = 1, message = "Duration must be at least 1 day")
    private Integer durationDays;

    @NotNull(message = "Session count is required")
    @Min(value = 1, message = "Session count must be at least 1")
    private Integer sessionCount;

    @NotNull(message = "Price amount is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Price amount must be non-negative")
    private BigDecimal priceAmount;

    private String description;
}
