package com.sportify.catalog.dto;

import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.RegistrationChannel;
import com.sportify.catalog.entity.TrainingFormat;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PackageRegistrationResponse {
    private Long id;
    private String registrationCode;
    private Long memberId;
    private String memberCode;
    private String memberFullName;
    private Long packageId;
    private String packageCode;
    private String packageName;
    private String sportName;
    private TrainingFormat trainingFormat;
    private RegistrationChannel channel;
    private BigDecimal originalPrice;
    private Integer discountPercentage;
    private BigDecimal paidAmount;
    private Integer totalSessions;
    private Integer remainingSessions;
    private LocalDate startDate;
    private LocalDate endDate;
    private PackageRegistrationStatus status;
    private LocalDateTime activatedAt;
    private LocalDateTime createdAt;
}
