package com.sportify.catalog.repository;

import jakarta.persistence.Entity;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;

/** Validate the real four mutation HQL statements and bindings without a JDBC connection. */
class ScheduledMaintenanceQueryTest {
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

    @ParameterizedTest
    @CsvSource({"card,cancelOverduePending", "card,expireActive", "registration,cancelOverduePending", "registration,expireActive"})
    void parsesRealMutationAndRequiresFlushClearAndTransaction(String kind, String name) throws Exception {
        Class<?> repository = kind.equals("card") ? MemberCardRepository.class : SportPackageRegistrationRepository.class;
        Class<?> boundType = name.equals("expireActive") ? LocalDate.class : LocalDateTime.class;
        var method = repository.getMethod(name, boundType, LocalDateTime.class);
        var modifying = method.getAnnotation(Modifying.class);
        assertThat(modifying.flushAutomatically()).isTrue();
        assertThat(modifying.clearAutomatically()).isTrue();
        assertThat(method.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.MANDATORY);
        try (var session = factory.openSession()) {
            var query = session.createMutationQuery(method.getAnnotation(Query.class).value());
            query.setParameter("now", LocalDateTime.of(2026, 10, 16, 0, 0));
            if (boundType == LocalDate.class) query.setParameter("today", LocalDate.of(2026, 10, 16));
            else query.setParameter("cutoff", LocalDateTime.of(2026, 10, 9, 0, 0));
            assertThat(query).isNotNull();
        }
    }
}
