package com.sportify.identity.repository;

import com.sportify.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SQL Server/Testcontainers only. Never targets an externally configured database. */
@AutoConfigureMockMvc
@Transactional
class UserAvatarStoreIntegrationTest extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserAvatarStore store;
    @Autowired private MockMvc mvc;

    private long account(String role) {
        String email = UUID.randomUUID() + "@avatar.test";
        jdbc.update("""
                INSERT INTO user_account (email, password_hash, full_name, role_id, status)
                SELECT ?, 'not-a-password', 'Avatar Fixture', id, 'ACTIVE' FROM [role] WHERE code = ?
                """, email, role);
        long id = jdbc.queryForObject("SELECT id FROM user_account WHERE email = ?", Long.class, email);
        jdbc.update("INSERT INTO coach_profile (user_id, is_public) VALUES (?, 1)", id);
        jdbc.update("UPDATE user_account SET avatar_url = ? WHERE id = ?", path(id) + "?v=1", id);
        store.save(id, "image/png", new byte[] {1, 2, 3});
        return id;
    }

    private static String path(long id) { return "/api/v1/users/" + id + "/avatar"; }

    @Test
    void servesOnlyCurrentlyPublishedActiveCoachAndRevokesWithoutCaching() throws Exception {
        long id = account("COACH");
        mvc.perform(get(path(id) + "?v=1")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"));
        jdbc.update("UPDATE coach_profile SET is_public = 0 WHERE user_id = ?", id);
        mvc.perform(get(path(id) + "?v=1")).andExpect(status().isNotFound());
        jdbc.update("UPDATE coach_profile SET is_public = 1 WHERE user_id = ?", id);
        jdbc.update("UPDATE user_account SET status = 'INACTIVE' WHERE id = ?", id);
        mvc.perform(get(path(id))).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "MANAGER", "RECEPTIONIST"})
    void otherRolesCannotPublishEvenWithALingeringPublicCoachProfile(String role) throws Exception {
        long id = account(role);
        assertThat(store.findPublicCoachAvatar(id)).isEmpty();
        mvc.perform(get(path(id))).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.test/photo.png", "/images/coaches/demo.jpg", "/api/v1/users/0/avatar?v=1", "", "/avatar?v=1"})
    void staleOrUnpublishedPhotoMetadataDoesNotExposeStoredBytes(String url) {
        long id = account("COACH");
        jdbc.update("UPDATE user_account SET avatar_url = ? WHERE id = ?", url, id);
        assertThat(store.findPublicCoachAvatar(id)).isEmpty();
    }

    @Test
    void missingProfileDeletedAccountAndUnknownIdDoNotPublish() {
        long id = account("COACH");
        jdbc.update("DELETE FROM coach_profile WHERE user_id = ?", id);
        assertThat(store.findPublicCoachAvatar(id)).isEmpty();
        jdbc.update("DELETE FROM user_account WHERE id = ?", id);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_avatar WHERE user_id = ?", Integer.class, id)).isZero();
        assertThat(store.findPublicCoachAvatar(id)).isEmpty();
    }

    @Test
    void corruptLengthMetadataIsNotServed() {
        long id = account("COACH");
        jdbc.update("UPDATE user_avatar SET size_bytes = 1 WHERE user_id = ?", id);
        assertThat(store.findPublicCoachAvatar(id)).isEmpty();
    }

    @Test
    void v33RejectsNonImageType() {
        long id = account("COACH");
        assertThatThrownBy(() -> jdbc.update("UPDATE user_avatar SET content_type = 'text/html' WHERE user_id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_user_avatar_type");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2097153})
    void v33RejectsInvalidStorageSize(int size) {
        long id = account("COACH");
        assertThatThrownBy(() -> jdbc.update("UPDATE user_avatar SET size_bytes = ? WHERE user_id = ?", size, id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_user_avatar_size");
    }

    @Test
    void v33RejectsUnknownOwner() {
        assertThatThrownBy(() -> store.save(-1L, "image/png", new byte[] {1}))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("fk_user_avatar_user");
    }
}
