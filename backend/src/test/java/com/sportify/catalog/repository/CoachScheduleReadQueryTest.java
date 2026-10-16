package com.sportify.catalog.repository;

import com.sportify.catalog.dto.CoachRosterResponse;
import com.sportify.catalog.dto.CoachSessionResponse;
import com.sportify.catalog.entity.BookingStatus;
import jakarta.persistence.Entity;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

/** Parse and bind real HQL with all entity mappings and SQL Server dialect, without JDBC. */
class CoachScheduleReadQueryTest {
    static SessionFactory factory;
    @BeforeAll static void metadata() throws Exception {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.SQLServerDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.hbm2ddl.auto", "none").build();
        try {
            var sources = new MetadataSources(registry);
            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
            for (var bean : scanner.findCandidateComponents("com.sportify")) {
                sources.addAnnotatedClass(Class.forName(bean.getBeanClassName()));
            }
            factory = sources.buildMetadata().buildSessionFactory();
        } catch (Exception error) {
            StandardServiceRegistryBuilder.destroy(registry);
            throw error;
        }
    }
    @AfterAll static void close() { if (factory != null) factory.close(); }
    static String hql(String method) {
        for (var candidate : CoachScheduleReadRepository.class.getMethods()) {
            if (candidate.getName().equals(method)) return candidate.getAnnotation(Query.class).value();
        }
        throw new IllegalArgumentException(method);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void scheduleProjectionParsesAndBindsNullableCoach(boolean supplied) {
        try (var session = factory.openSession()) {
            var query = session.createQuery(hql("findTeachingSessions"), CoachSessionResponse.class)
                    .setParameter("from", LocalDate.of(9999, 12, 31))
                    .setParameter("to", LocalDate.of(9999, 12, 31))
                    .setParameter("coachId", supplied ? 17L : null);
            assertThat(query.getParameters()).hasSize(3);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void rosterProjectionParsesAndBindsOwnerStatusAndManager(boolean manager) {
        try (var session = factory.openSession()) {
            var query = session.createQuery(hql("findRoster"), CoachRosterResponse.class)
                    .setParameter("sessionId", 91L).setParameter("status", BookingStatus.CONFIRMED)
                    .setParameter("manager", manager).setParameter("coachId", manager ? null : 17L);
            assertThat(query.getParameters()).hasSize(4);
        }
    }

    @Test void ownershipProjectionParsesWithoutRosterOrMemberJoin() {
        try (var session = factory.openSession()) {
            assertThat(session.createQuery(hql("findOwnership")).setParameter("id", 91L).getParameters()).hasSize(1);
        }
        assertThat(hql("findOwnership")).doesNotContain("Booking", "MemberProfile", "phone");
    }
}
