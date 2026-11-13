package com.sportify.payment.service;

import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.config.BankTransferProperties;
import com.sportify.payment.dto.PaymentResponse;
import com.sportify.payment.entity.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VietQrTransferServiceTest {
    @Test void configuredSupportedBeneficiaryProducesInstructionsWithoutSettlement() {
        var payments=mock(PaymentService.class); var directory=mock(VietQrBankDirectory.class);
        var actor=UserAccount.builder().id(10L).build();
        var config=new BankTransferProperties(); config.setLiveInstructionsEnabled(true); config.setBankBin("970415");
        config.setAccountNumber("113366668888"); config.setAccountName("OFFICIAL FORMAT FIXTURE");
        var payment=PaymentResponse.builder().id(20L).memberId(10L).code("PAY-20").amount(new BigDecimal("79000"))
                .status(PaymentStatus.PENDING).method(PaymentMethod.BANK_TRANSFER).settlementType("ACTUAL").build();
        when(payments.getPayment(20L,actor)).thenReturn(payment);
        when(directory.supported("970415")).thenReturn(Optional.of(new VietQrBankDirectory.Bank("970415","ICB","VietinBank",true)));
        var result=new VietQrTransferService(payments,config,directory).instructions(20L,actor);
        assertThat(result.payable()).isTrue(); assertThat(result.transferReference()).isEqualTo("SP20");
        assertThat(result.qrPayload()).startsWith("000201010212").contains("QRIBFTTA");
        assertThat(result.requiresStaffConfirmation()).isTrue(); assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(payments).getPayment(20L,actor); verifyNoMoreInteractions(payments);
    }
    @Test void missingConfigurationOrDirectoryNeverReturnsAnActionableQr() {
        var payments=mock(PaymentService.class); var directory=mock(VietQrBankDirectory.class);
        var actor=UserAccount.builder().id(10L).build();
        when(payments.getPayment(20L,actor)).thenReturn(PaymentResponse.builder().id(20L).memberId(10L).code("PAY-20")
                .amount(BigDecimal.TEN).status(PaymentStatus.PENDING).method(PaymentMethod.BANK_TRANSFER).build());
        var result=new VietQrTransferService(payments,new BankTransferProperties(),directory).instructions(20L,actor);
        assertThat(result.payable()).isFalse(); assertThat(result.qrPayload()).isNull(); assertThat(result.accountNumber()).isNull();
        assertThat(result.notice()).contains("DEMO NON-PAYABLE");
    }
}
