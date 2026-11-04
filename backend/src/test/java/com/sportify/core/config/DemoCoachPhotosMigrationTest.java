package com.sportify.core.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static com.sportify.core.config.DemoSeedSource.ROOT;
import static com.sportify.core.config.DemoSeedSource.resource;
import static org.assertj.core.api.Assertions.assertThat;

class DemoCoachPhotosMigrationTest {
    private static final String MIGRATION = "db/demo/V41__assign_demo_coach_photos.sql";
    private static final String SQL = resource(MIGRATION);

    @Test
    void migrationTargetsOnlyTheThreeDemoCoachesAndBundledAssetsExist() {
        assertThat(SQL).contains(
                "('nam.coach@example.com', '/images/coaches/long-pham.jpg')",
                "('ha.coach@example.com', '/images/coaches/tram-vo.jpg')",
                "('quan.coach@example.com', '/images/coaches/bao-le.jpg')");
        assertThat(SQL.lines().filter(line -> line.contains("@example.com")).toList()).hasSize(3);
        assertThat(SQL).contains("r.code = 'COACH'");

        for (String asset : new String[]{"long-pham.jpg", "tram-vo.jpg", "bao-le.jpg"}) {
            assertThat(Files.isRegularFile(ROOT.resolve("frontend/public/images/coaches").resolve(asset)))
                    .as("bundled coach photo %s", asset).isTrue();
        }
    }

    @Test
    void migrationPreservesEveryExistingManagerSelectedPhoto() {
        assertThat(SQL).contains("WHERE u.avatar_url IS NULL OR LTRIM(RTRIM(u.avatar_url)) = ''");
        assertThat(SQL).doesNotContain("ELSE", "DELETE FROM", "INSERT INTO");
    }

    @Test
    void migrationIsAvailableOnlyThroughTheExplicitDemoProfile() {
        assertThat(resource("application-demo.yml")).contains("classpath:db/demo");
        assertThat(resource("application.yml")).doesNotContain("classpath:db/demo");
        assertThat(resource("application-prod.yml")).doesNotContain("classpath:db/demo");
    }
}
