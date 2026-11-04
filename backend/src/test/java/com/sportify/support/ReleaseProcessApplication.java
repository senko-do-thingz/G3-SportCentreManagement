package com.sportify.support;

import com.sportify.SportifyApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import java.time.*;

/** Test-classpath-only process entry point. Never packaged in the runtime application. */
public final class ReleaseProcessApplication {
    public static void main(String[] args) {
        new SpringApplicationBuilder(SportifyApplication.class,TimeConfiguration.class).run(args);
    }
    @TestConfiguration public static class TimeConfiguration {
        @Bean @Primary Clock releaseProcessClock() {
            return Clock.fixed(Instant.parse(System.getenv("SPORTIFY_RELEASE_TEST_TIME")),ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }
}
