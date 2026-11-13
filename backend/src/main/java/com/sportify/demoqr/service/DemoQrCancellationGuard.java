package com.sportify.demoqr.service;

import com.sportify.payment.entity.Payment;
import com.sportify.payment.service.PaymentCancellationGuard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Sportify demo QR: a session that was scanned and authorised, or already settled, means settlement has started, so
 * the Member can no longer cancel. A QR that is only displayed (PENDING) is closed when the payment is cancelled.
 * The caller holds the payment lock; the demo QR confirm path also locks the payment before its session.
 */
@Component
public class DemoQrCancellationGuard implements PaymentCancellationGuard {
    static final String BLOCKED = "A QR payment for this order is already being settled and can no longer be cancelled";

    private final JdbcTemplate jdbc;

    public DemoQrCancellationGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String blockedReason(Payment payment) {
        Integer started = jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM dbo.simulated_qr_session
                        WHERE payment_id=? AND state IN ('AUTHORIZED_PENDING_SETTLEMENT','SETTLED'))
                     + (SELECT COUNT(*) FROM dbo.simulated_payment_transaction WHERE payment_id=?)
                """, Integer.class, payment.getId(), payment.getId());
        return started != null && started > 0 ? BLOCKED : null;
    }

    @Override
    public void onCancelled(Payment payment) {
        jdbc.update("""
                UPDATE dbo.simulated_qr_session SET state='CANCELED',active=0,version=version+1,
                state_changed_at_utc=SYSUTCDATETIME() WHERE payment_id=? AND active=1 AND state='PENDING'
                """, payment.getId());
    }
}
