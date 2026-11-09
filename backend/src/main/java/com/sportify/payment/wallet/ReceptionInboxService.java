package com.sportify.payment.wallet;

import com.sportify.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * MW-07 Receptionist action inbox on the existing notification table. Each open source task (wallet top-up to verify,
 * withdrawal to review or pay, payment with an amount mismatch, open support request) gets one notification per
 * Receptionist, keyed by the existing unique (user_id, event_key) index, so polling never duplicates a notice. Read is
 * presentation state only: whether the task is still open is read live from the source row, never from the notice.
 * Notices carry codes and amounts only, never bank account numbers or health data.
 */
@Service
@RequiredArgsConstructor
public class ReceptionInboxService {

    static final String TYPE = "RECEPTION_TASK";

    public record Item(long id, String title, String content, LocalDateTime createdAt, boolean read, String sourceType,
                       long sourceId, String sourceStatus, boolean open, String link) {}

    public record Inbox(List<Item> items, long unreadOpen, long open) {}

    private final JdbcTemplate jdbc;

    /** Source queries for currently open tasks: (event key, title, content, created_at). */
    private static final String OPEN_TASKS = """
            SELECT 'WALLET_TOPUP:' + CAST(t.id AS VARCHAR(20)) AS event_key,
                   N'Verify wallet top-up ' + t.topup_code AS title,
                   CONCAT(N'Member ', m.member_code, N' requested ', FORMAT(t.amount, 'N0'), N' VND',
                          CASE WHEN t.mode = 'SIMULATED' THEN N' (demo funds)' ELSE N'. Check the bank statement.' END) AS content,
                   t.created_at
            FROM dbo.wallet_topup t JOIN dbo.member_profile m ON m.user_id = t.member_id WHERE t.status = 'PENDING'
            UNION ALL
            SELECT 'WALLET_WITHDRAWAL:' + CAST(w.id AS VARCHAR(20)),
                   N'Review wallet withdrawal ' + w.withdrawal_code,
                   CONCAT(N'Member ', m.member_code, N' asked to withdraw ', FORMAT(w.amount, 'N0'), N' VND',
                          CASE WHEN w.pool = 'SIMULATED' THEN N' (demo funds)' ELSE N'.' END),
                   w.created_at
            FROM dbo.wallet_withdrawal w JOIN dbo.member_profile m ON m.user_id = w.member_id WHERE w.status IN ('PENDING', 'APPROVED')
            UNION ALL
            SELECT 'PAYMENT_EXCEPTION:' + CAST(p.id AS VARCHAR(20)),
                   N'Payment needs review ' + p.payment_code,
                   N'An amount mismatch was recorded. The payment is still pending.',
                   p.updated_at
            FROM dbo.payment p WHERE p.payment_status = 'PENDING' AND p.notes LIKE N'%Amount mismatch:%'
            UNION ALL
            SELECT 'SUPPORT_REQUEST:' + CAST(s.id AS VARCHAR(20)),
                   N'Support request ' + s.request_code,
                   N'Open member support request: ' + LEFT(s.subject, 120),
                   s.created_at
            FROM dbo.support_request s WHERE s.status IN ('OPEN', 'IN_PROGRESS')
            """;

    /**
     * Creates any missing notice for this Receptionist. Inserting by the unique event key makes repeated polling and
     * concurrent calls harmless; a staff member added later still receives every task that is open now.
     */
    @Transactional
    public void sync(long receptionistId) {
        jdbc.update("""
                INSERT INTO dbo.notification (user_id, title, content, type, is_read, created_at, event_key)
                SELECT ?, LEFT(o.title, 200), LEFT(o.content, 1000), ?, 0, o.created_at, o.event_key
                FROM (""" + OPEN_TASKS + """
                ) o
                WHERE NOT EXISTS (SELECT 1 FROM dbo.notification n WITH (UPDLOCK, HOLDLOCK)
                                  WHERE n.user_id = ? AND n.event_key = o.event_key)
                """, receptionistId, TYPE, receptionistId);
    }

    @Transactional
    public Inbox inbox(long receptionistId, boolean openOnly) {
        sync(receptionistId);
        List<Item> items = jdbc.query("""
                SELECT TOP 100 n.id, n.title, n.content, n.created_at, n.is_read, n.event_key FROM dbo.notification n
                WHERE n.user_id = ? AND n.type = ? ORDER BY n.created_at DESC, n.id DESC""",
                (rs, row) -> item(rs.getLong("id"), rs.getString("title"), rs.getString("content"),
                        rs.getTimestamp("created_at").toLocalDateTime(), rs.getBoolean("is_read"), rs.getString("event_key")),
                receptionistId, TYPE);
        List<Item> shown = openOnly ? items.stream().filter(Item::open).toList() : items;
        long open = items.stream().filter(Item::open).count();
        long unreadOpen = items.stream().filter(i -> i.open() && !i.read()).count();
        return new Inbox(shown, unreadOpen, open);
    }

    /** Marks the Receptionist's own notice read. It never changes the task it points to. */
    @Transactional
    public Item markRead(long receptionistId, long notificationId) {
        int updated = jdbc.update("UPDATE dbo.notification SET is_read = 1 WHERE id = ? AND user_id = ? AND type = ?",
                notificationId, receptionistId, TYPE);
        if (updated == 0) {
            throw new ResourceNotFoundException("Notice not found");
        }
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM dbo.notification WHERE id = ?", notificationId);
        return item(notificationId, (String) row.get("title"), (String) row.get("content"),
                ((java.sql.Timestamp) row.get("created_at")).toLocalDateTime(), true, (String) row.get("event_key"));
    }

    private Item item(long id, String title, String content, LocalDateTime createdAt, boolean read, String eventKey) {
        String[] parts = eventKey == null ? new String[] {"UNKNOWN", "0"} : eventKey.split(":", 2);
        String type = parts[0];
        long sourceId = Long.parseLong(parts[1]);
        String status = switch (type) {
            case "WALLET_TOPUP" -> status("SELECT status FROM dbo.wallet_topup WHERE id = ?", sourceId);
            case "WALLET_WITHDRAWAL" -> status("SELECT status FROM dbo.wallet_withdrawal WHERE id = ?", sourceId);
            case "PAYMENT_EXCEPTION" -> status("SELECT payment_status FROM dbo.payment WHERE id = ?", sourceId);
            case "SUPPORT_REQUEST" -> status("SELECT status FROM dbo.support_request WHERE id = ?", sourceId);
            default -> "UNKNOWN";
        };
        boolean open = switch (type) {
            case "WALLET_TOPUP", "PAYMENT_EXCEPTION" -> "PENDING".equals(status);
            case "WALLET_WITHDRAWAL" -> "PENDING".equals(status) || "APPROVED".equals(status);
            case "SUPPORT_REQUEST" -> "OPEN".equals(status) || "IN_PROGRESS".equals(status);
            default -> false;
        };
        String link = switch (type) {
            case "WALLET_TOPUP" -> "/reception/wallet/topups";
            case "WALLET_WITHDRAWAL" -> "/reception/wallet/withdrawals";
            case "PAYMENT_EXCEPTION" -> "/reception/payments/" + sourceId;
            case "SUPPORT_REQUEST" -> "/reception/support";
            default -> "/reception";
        };
        return new Item(id, title, content, createdAt, read, type, sourceId, status, open, link);
    }

    private String status(String sql, long id) {
        List<String> found = jdbc.queryForList(sql, String.class, id);
        return found.isEmpty() ? "UNKNOWN" : found.getFirst();
    }
}
