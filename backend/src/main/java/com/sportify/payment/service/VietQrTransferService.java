package com.sportify.payment.service;

import com.sportify.catalog.service.OperationalMemberPolicy;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.config.BankTransferProperties;
import com.sportify.payment.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class VietQrTransferService {
    private final PaymentService payments;
    private final BankTransferProperties beneficiary;
    private final VietQrBankDirectory directory;
    private final OperationalMemberPolicy operational;
    public record Instructions(long paymentId,String paymentCode,boolean payable,String notice,String bankBin,
            String bankName,String accountNumber,String accountName,BigDecimal amount,String currency,
            String transferReference,String qrPayload,boolean requiresStaffConfirmation) {}

    @Transactional(readOnly=true)
    public Instructions instructions(long id,UserAccount actor) {
        var payment=payments.getPayment(id,actor);
        operational.requireRealMember(payment.getMemberId());
        if(payment.getStatus()!=PaymentStatus.PENDING || payment.getMethod()!=PaymentMethod.BANK_TRANSFER
                || "SIMULATED".equals(payment.getSettlementType()) || payment.getSimulatedTransactionId()!=null
                || "SPORTIFY_SIMULATOR".equals(payment.getSettlementProvider()))
            throw new BusinessRuleException("Only pending real bank-transfer payments support these instructions","TRANSFER_NOT_AVAILABLE");
        var bank=directory.supported(beneficiary.getBankBin());
        if(!beneficiary.isLiveInstructionsEnabled() || !beneficiary.isLiveBeneficiaryValid() || bank.isEmpty())
            return new Instructions(id,payment.getCode(),false,"DEMO NON-PAYABLE. A verified beneficiary and fresh supported bank directory are required.",
                    null,null,null,null,payment.getAmount(),"VND",null,null,true);
        String reference="SP"+id;
        String account=beneficiary.getAccountNumber().replace(" ","");
        String qr;
        try { qr=VietQrPayload.encode(bank.get().bin(),account,payment.getAmount(),reference); }
        catch(IllegalArgumentException invalid) { throw new BusinessRuleException("Payment amount cannot be represented as a VietQR VND transfer","TRANSFER_NOT_AVAILABLE"); }
        return new Instructions(id,payment.getCode(),true,"Transfer instructions only. Staff must verify actual receipt before confirming payment.",
                bank.get().bin(),bank.get().name(),account,beneficiary.getAccountName(),payment.getAmount(),"VND",reference,qr,true);
    }
}
