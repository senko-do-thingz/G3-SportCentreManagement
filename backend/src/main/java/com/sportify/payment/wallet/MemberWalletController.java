package com.sportify.payment.wallet;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.payment.wallet.WalletLedgerService.Balance;
import com.sportify.payment.wallet.WalletLedgerService.Entry;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** MW-03: the signed-in Member's own wallet. There is no member id in any path, so no other wallet is reachable. */
@RestController
@RequestMapping("/api/v1/member/wallet")
@PreAuthorize("hasRole('MEMBER') and principal.enabled")
@RequiredArgsConstructor
public class MemberWalletController {

    private final WalletLedgerService ledger;
    private final WalletHistoryService history;

    public record Overview(BigDecimal availableBalance, BigDecimal reservedBalance, BigDecimal demoAvailableBalance,
                           BigDecimal demoReservedBalance, String currency, LocalDateTime updatedAt,
                           List<Entry> recentActivity, long activityCount, String notice) {}

    @GetMapping
    public Overview overview(@AuthenticationPrincipal CustomUserDetails user) {
        long memberId = user.getUserAccount().getId();
        Balance balance = ledger.balance(memberId);
        return new Overview(balance.actualAvailable(), balance.actualReserved(), balance.simulatedAvailable(),
                balance.simulatedReserved(), "VND", balance.updatedAt(), ledger.entries(memberId, 0, 5),
                ledger.entryCount(memberId),
                "Sportify Wallet is an internal balance at this center. Demo funds are not real money and cannot be withdrawn to a bank.");
    }

    @GetMapping("/ledger")
    public PageResponse<Entry> ledger(@AuthenticationPrincipal CustomUserDetails user,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        long memberId = user.getUserAccount().getId();
        ledger.balance(memberId);
        List<Entry> content = ledger.entries(memberId, page, size);
        long total = ledger.entryCount(memberId);
        return new PageResponse<>(content, page, size, total, (int) ((total + size - 1) / size));
    }

    /** MW-08 wallet activity of the signed-in Member only, with filters and a ledger reconciliation flag. */
    @GetMapping("/history")
    public WalletHistoryService.History history(@AuthenticationPrincipal CustomUserDetails user,
                                                @RequestParam(required = false) String type,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return history.history(user.getUserAccount().getId(), type, status, from, to, page, size);
    }
}
