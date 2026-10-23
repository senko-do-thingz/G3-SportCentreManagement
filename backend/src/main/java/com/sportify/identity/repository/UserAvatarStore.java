package com.sportify.identity.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Uploaded profile photos in table user_avatar (V33). Plain JDBC keeps the binary column out of the JPA model.
 */
@Repository
@RequiredArgsConstructor
public class UserAvatarStore {

    /** Photo bytes with their detected media type. */
    public record StoredAvatar(String contentType, byte[] data) {
    }

    private final JdbcTemplate jdbcTemplate;

    /** Inserts or replaces the photo of one account. */
    public void save(Long userId, String contentType, byte[] data) {
        int updated = jdbcTemplate.update(
                "UPDATE user_avatar SET content_type = ?, size_bytes = ?, data = ?, updated_at = SYSDATETIME() WHERE user_id = ?",
                contentType, data.length, data, userId);
        if (updated == 0) {
            jdbcTemplate.update("INSERT INTO user_avatar (user_id, content_type, size_bytes, data) VALUES (?, ?, ?, ?)",
                    userId, contentType, data.length, data);
        }
    }

    /** One authoritative read enforces publication, account role/status and the current uploaded-photo link. */
    public Optional<StoredAvatar> findPublicCoachAvatar(Long userId) {
        String path = "/api/v1/users/" + userId + "/avatar";
        return jdbcTemplate.query("""
                        SELECT a.content_type, a.data FROM user_avatar a
                        JOIN user_account u ON u.id = a.user_id
                        JOIN [role] r ON r.id = u.role_id
                        JOIN coach_profile cp ON cp.user_id = u.id
                        WHERE u.id = ? AND u.status = 'ACTIVE' AND r.code = 'COACH' AND cp.is_public = 1
                          AND a.content_type IN ('image/jpeg', 'image/png', 'image/webp')
                          AND a.size_bytes BETWEEN 1 AND 2097152 AND a.size_bytes = DATALENGTH(a.data)
                          AND (u.avatar_url = ? OR
                            (LEFT(u.avatar_url, LEN(?) + 3) = CONCAT(?, '?v=')
                             AND LEN(u.avatar_url) > LEN(?) + 3
                             AND SUBSTRING(u.avatar_url, LEN(?) + 4, 500) NOT LIKE '%[^0-9]%'))
                        """,
                        (rs, row) -> new StoredAvatar(rs.getString("content_type"), rs.getBytes("data")),
                        userId, path, path, path, path, path)
                .stream().findFirst();
    }

    public void delete(Long userId) {
        jdbcTemplate.update("DELETE FROM user_avatar WHERE user_id = ?", userId);
    }
}
