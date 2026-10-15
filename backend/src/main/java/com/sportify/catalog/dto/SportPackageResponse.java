package com.sportify.catalog.dto;

import com.sportify.catalog.entity.TrainingFormat;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SportPackageResponse {
    private Long id;
    private String code;
    private String name;
    private Long sportId;
    private String sportName;
    private String sportCode;
    private TrainingFormat trainingFormat;
    private Integer durationDays;
    private Integer sessionCount;
    private Integer sessionMinutes;
    private BigDecimal priceAmount;
    private String description;
    private Boolean isActive;
}
