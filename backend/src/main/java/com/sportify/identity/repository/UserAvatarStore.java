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

    public Optional<StoredAvatar> find(Long userId) {
        return jdbcTemplate.query("SELECT content_type, data FROM user_avatar WHERE user_id = ?",
                        (rs, row) -> new StoredAvatar(rs.getString("content_type"), rs.getBytes("data")), userId)
                .stream().findFirst();
    }

    public void delete(Long userId) {
        jdbcTemplate.update("DELETE FROM user_avatar WHERE user_id = ?", userId);
    }
}
