package com.sportify.catalog.service.impl;

import org.junit.jupiter.api.Test;
import org.mockito.exceptions.base.MockitoException;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

/** Reproduce the two proxy/stubbing defects without a database; real race coverage remains SQL Server-only. */
class MaintenanceRepositorySpyRegressionTest {
    interface Repository {
        Optional<Long> findById(Long id);
        @Transactional(propagation = Propagation.MANDATORY)
        int expireActive(LocalDate day, LocalDateTime now);
    }

    @Test void callRealMethodOnAnAbstractDelegatingRepositoryCannotReleaseTheReadBarrier() {
        Repository delegate = mock(Repository.class);
        when(delegate.findById(7L)).thenReturn(Optional.of(7L));
        Repository spy = mock(Repository.class,delegatesTo(delegate));
        assertThat(spy.findById(7L)).contains(7L);
        doAnswer(call -> call.callRealMethod()).when(spy).findById(7L);
        assertThatThrownBy(() -> spy.findById(7L)).isInstanceOf(MockitoException.class).hasMessageContaining("abstract");
    }

    @Test void mandatoryTransactionAdviceRunsBeforeTheUnderlyingSpyCanConsumeMatchers() {
        Repository target = mock(Repository.class);
        var transactionManager = new DataSourceTransactionManager(mock(DataSource.class));
        var factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactionManager,new AnnotationTransactionAttributeSource()));
        Repository proxy = (Repository) factory.getProxy();
        try {
            assertThatThrownBy(() -> doThrow(new IllegalStateException("Synthetic fourth failure"))
                    .when(proxy).expireActive(any(LocalDate.class),any(LocalDateTime.class)))
                    .isInstanceOf(IllegalTransactionStateException.class).hasMessageContaining("mandatory");
            assertThatThrownBy(() -> reset(target)).isInstanceOf(MockitoException.class);
        } finally {
            // The deliberately broken stubbing must not contaminate other unit tests.
            for (int i=0;i<3;i++) { try { validateMockitoUsage(); } catch (MockitoException ignored) { } }
            reset(target);
        }
    }
}
