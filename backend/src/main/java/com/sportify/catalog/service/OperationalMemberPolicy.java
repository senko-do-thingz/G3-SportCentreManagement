package com.sportify.catalog.service;

import com.sportify.core.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Synthetic identities and settlement provenance never grant live operational access. */
@Service
@RequiredArgsConstructor
public class OperationalMemberPolicy {
    private final JdbcTemplate jdbc;
    private final Environment environment;

    public void requireRealMember(long memberId) {
        // Read the configured reservation even when simulation is disabled. Historical provenance
        // survives profile changes and email renames. A mixed real/synthetic account is quarantined.
        String reserved = environment.getProperty("app.payment.unified-demo.member-ids", "");
        boolean configured = java.util.Arrays.stream(reserved.split(","))
                .anyMatch(id -> id.trim().equals(Long.toString(memberId)));
        Integer synthetic = jdbc.queryForObject("""
                SELECT CASE WHEN EXISTS (SELECT 1 FROM dbo.user_account WHERE id=?
                    AND LOWER(email) LIKE '%@unified-demo.invalid')
                  OR EXISTS (SELECT 1 FROM dbo.payment p WHERE p.member_id=? AND
                    (p.settlement_type='SIMULATED' OR p.settlement_provider='SPORTIFY_SIMULATOR'
                     OR p.simulated_transaction_id IS NOT NULL)
                    AND NOT EXISTS (SELECT 1 FROM dbo.operational_qr_settlement oq
                        WHERE oq.payment_id=p.id AND oq.simulated_transaction_id=p.simulated_transaction_id))
                  OR EXISTS (SELECT 1 FROM dbo.simulated_payment_transaction tx WHERE tx.member_id=?
                    AND NOT EXISTS (SELECT 1 FROM dbo.operational_qr_settlement oq
                        WHERE oq.payment_id=tx.payment_id AND oq.simulated_transaction_id=tx.id))
                THEN 1 ELSE 0 END
                """, Integer.class, memberId, memberId, memberId);
        if (configured || Integer.valueOf(1).equals(synthetic)) {
            throw new BusinessRuleException("Synthetic members cannot use operational classes or training records",
                    "SYNTHETIC_OPERATION_DENIED");
        }
    }

    public boolean isSyntheticMember(long memberId) {
        try {
            requireRealMember(memberId);
            return false;
        } catch (BusinessRuleException ex) {
            if ("SYNTHETIC_OPERATION_DENIED".equals(ex.getCode())) {
                return true;
            }
            throw ex;
        }
    }

    public void requireRealBooking(long bookingId) {
        jdbc.queryForList("SELECT member_id FROM dbo.booking WHERE id=?", Long.class, bookingId)
                .forEach(this::requireRealMember);
        // Also check the entitlement owner, so a malformed historical association cannot bypass isolation.
        jdbc.queryForList("""
                SELECT p.member_id FROM dbo.booking b
                JOIN dbo.sport_package_registration r ON r.id=b.package_registration_id
                JOIN dbo.payment p ON p.id=r.payment_id WHERE b.id=?
                """, Long.class, bookingId).forEach(this::requireRealMember);
    }
}
