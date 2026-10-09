package com.sportify.catalog.dto;

import com.sportify.catalog.entity.RegistrationChannel;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PackageRegistrationRequest {

    private Long memberId;

    @NotNull(message = "Package ID is required")
    private Long packageId;

    private RegistrationChannel channel;

    /** Member or receptionist explicitly chooses the start date. Defaults to today if omitted. */
    private LocalDate startDate;
}
