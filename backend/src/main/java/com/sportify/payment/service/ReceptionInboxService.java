package com.sportify.payment.service;

import com.sportify.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Đồng bộ thông báo công việc của Receptionist từ payment đang cần kiểm tra và support request còn mở.
 * Khóa duy nhất (user_id, event_key) chống thông báo trùng khi tải lại hoặc có yêu cầu đồng thời.
 * Trạng thái đọc chỉ phục vụ giao diện; trạng thái công việc lấy trực tiếp từ bảng nghiệp vụ.
 */
@Service
@RequiredArgsConstructor
public class ReceptionInboxService {

    static final String TYPE = "RECEPTION_TASK";

    public record Item(long id, String title, String content, LocalDateTime createdAt, boolean read, String sourceType,
                       long sourceId, String sourceStatus, boolean open, String link) {}

    public record Inbox(List<Item> items, long unreadOpen, long open) {}

    private final JdbcTemplate jdbc;

    /** Truy vấn chỉ lấy công việc thực sự còn mở; các nguồn đã ngừng phục vụ không được tạo thông báo mới. */
    private static final String OPEN_TASKS = """
            SELECT 'PAYMENT_EXCEPTION:' + CAST(p.id AS VARCHAR(20)) AS event_key,
                   N'Payment needs review ' + p.payment_code AS title,
                   N'An amount mismatch was recorded. The payment is still pending.' AS content,
                   p.updated_at AS created_at
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
    // Ghi thông báo thiếu vào cùng giao dịch để lần refresh tiếp theo không tạo bản ghi lặp.
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

    // Đếm tác vụ chưa đọc và còn mở từ trạng thái thực tế, không đếm theo riêng cờ is_read.
    @Transactional
    public Inbox inbox(long receptionistId, boolean openOnly) {
        sync(receptionistId);
        List<Item> items = jdbc.query("""
                SELECT TOP 100 n.id, n.title, n.content, n.created_at, n.is_read, n.event_key FROM dbo.notification n
                WHERE n.user_id = ? AND n.type = ?
                  AND (n.event_key LIKE 'PAYMENT_EXCEPTION:%' OR n.event_key LIKE 'SUPPORT_REQUEST:%')
                ORDER BY n.created_at DESC, n.id DESC""",
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
            case "PAYMENT_EXCEPTION" -> status("SELECT payment_status FROM dbo.payment WHERE id = ?", sourceId);
            case "SUPPORT_REQUEST" -> status("SELECT status FROM dbo.support_request WHERE id = ?", sourceId);
            default -> "UNKNOWN";
        };
        boolean open = switch (type) {
            case "PAYMENT_EXCEPTION" -> "PENDING".equals(status);
            case "SUPPORT_REQUEST" -> "OPEN".equals(status) || "IN_PROGRESS".equals(status);
            default -> false;
        };
        String link = switch (type) {
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
