package com.sportify.catalog.repository;

import com.sportify.catalog.entity.ClassSession;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.entity.TrainingFormat;
import jakarta.persistence.Entity;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static com.sportify.catalog.test.ScheduleTestData.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Parse and bind the real repository HQL with actual entity metadata and SQL Server dialect, without JDBC. */
class MemberScheduleQueryTest {
    private static SessionFactory factory;

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

    private static Query annotation(String name) {
        for (Method method : ClassSessionRepository.class.getMethods()) {
            if (method.getName().equals(name)) return method.getAnnotation(Query.class);
        }
        throw new IllegalArgumentException(name);
    }

    @Test void searchAndCountParseAndBindAllTypedFiltersWithNullsAndValues() {
        var annotation = annotation("searchVisible");
        try (var session = factory.openSession()) {
            for (boolean supplied : new boolean[]{false, true}) {
                for (String hql : new String[]{annotation.value(), annotation.countQuery()}) {
                    var query = session.createQuery(hql);
                    query.setParameter("sportId", supplied ? 10L : null);
                    query.setParameter("from", supplied ? TODAY : null);
                    query.setParameter("to", supplied ? TODAY.plusDays(7) : null);
                    query.setParameter("format", supplied ? TrainingFormat.COACH_LED : null);
                    query.setParameter("level", supplied ? "BEGINNER" : null);
                    query.setParameter("coachId", supplied ? 20L : null);
                    query.setParameter("today", TODAY);
                    query.setParameter("now", NOW);
                    query.setFirstResult(0).setMaxResults(20);
                    assertThat(query.getParameters()).hasSize(8);
                }
            }
        }
    }

    @Test void detailParsesAndBindsClockWithoutLoadingOrLockingAnything() {
        try (var session = factory.openSession()) {
            var query = session.createQuery(annotation("findVisibleById").value(), ClassSession.class);
            query.setParameter("id", 100L).setParameter("today", TODAY).setParameter("now", NOW);
            assertThat(query.getParameters()).hasSize(3);
        }
    }

    @Test void ownedRegistrationQueryParsesWithActualFormatAndStatusMappings() throws Exception {
        var annotation = SportPackageRegistrationRepository.class.getMethod("findForSessionEligibility", Long.class, Long.class, TrainingFormat.class)
                .getAnnotation(Query.class);
        try (var session = factory.openSession()) {
            var query = session.createQuery(annotation.value(), SportPackageRegistration.class);
            query.setParameter("memberId", 2L).setParameter("sportId", 10L).setParameter("format", TrainingFormat.COACH_LED);
            assertThat(query.getParameters()).hasSize(3);
        }
    }

    @Test void bookingHistoryAndCountParseAndBindAllPeriodsWithActualSqlServerMappings() throws Exception {
        var queryAnnotation = BookingRepository.class.getMethod("findMyBookings", Long.class, String.class,
                LocalDate.class, java.time.LocalTime.class, org.springframework.data.domain.Pageable.class).getAnnotation(Query.class);
        try (var session = factory.openSession()) {
            var legacy = BookingRepository.class.getMethod("findByMemberIdOrderByBookedAtDesc", Long.class).getAnnotation(Query.class);
            assertThat(session.createQuery(legacy.value()).setParameter("memberId", 30L).getParameters()).hasSize(1);
            for (String period : new String[]{null, "UPCOMING", "PAST"}) {
                for (String hql : new String[]{queryAnnotation.value(), queryAnnotation.countQuery()}) {
                    var query = session.createQuery(hql);
                    query.setParameter("memberId", 30L).setParameter("period", period)
                            .setParameter("today", TODAY).setParameter("now", NOW);
                    assertThat(query.getParameters()).hasSize(4);
                }
            }
        }
    }

    @Test void staffHistoryContentAndCountBindIdenticalCombinedFilters() throws Exception {
        var annotation = BookingRepository.class.getMethod("findMemberBookings", Long.class, String.class,
                com.sportify.catalog.entity.BookingStatus.class, LocalDate.class, java.time.LocalTime.class,
                org.springframework.data.domain.Pageable.class).getAnnotation(Query.class);
        try (var session = factory.openSession()) {
            for (String period : new String[]{null, "UPCOMING", "PAST"}) {
                for (var status : new com.sportify.catalog.entity.BookingStatus[]{null,
                        com.sportify.catalog.entity.BookingStatus.CONFIRMED, com.sportify.catalog.entity.BookingStatus.CANCELLED}) {
                    for (String hql : new String[]{annotation.value(), annotation.countQuery()}) {
                        var query = session.createQuery(hql);
                        query.setParameter("memberId", 2L).setParameter("period", period).setParameter("status", status)
                                .setParameter("today", TODAY).setParameter("now", NOW);
                        assertThat(query.getParameters()).hasSize(5);
                    }
                }
            }
        }
    }
}
