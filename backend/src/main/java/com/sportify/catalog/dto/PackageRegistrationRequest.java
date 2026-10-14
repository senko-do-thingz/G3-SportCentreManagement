package com.sportify.catalog.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.sportify.catalog.entity.RegistrationChannel;
import com.sportify.payment.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
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

    /** Online: the method the member will pay with. Desk sale (staff): the method used to pay now, CASH when omitted. */
    @JsonAlias({"method"})
    private PaymentMethod paymentMethod;

    /** Bank, card slip or e-wallet reference of a desk sale (BE-19). Required for every method except CASH. */
    @Size(max = 100, message = "Reference code must be at most 100 characters")
    @JsonAlias({"paymentReference", "reference"})
    private String referenceCode;

    /** Money handed over at the desk (BE-19). Cash may be more than the price; the amount itself when omitted. */
    @DecimalMin(value = "0.01", message = "Amount received must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Amount received must have at most 10 integer digits and 2 decimals")
    private BigDecimal amountReceived;
}
