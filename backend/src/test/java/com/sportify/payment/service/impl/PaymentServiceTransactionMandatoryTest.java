package com.sportify.payment.service.impl;

import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.core.common.CodeFormatter;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.config.BankTransferProperties;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.repository.PaymentRepository;
import com.sportify.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.math.BigDecimal;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PaymentServiceTransactionMandatoryTest {

    private static class NoTransactionPlatformTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return false;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
    }

    private PaymentService proxy;

    @BeforeEach
    void setUpProxy() {
        PaymentServiceImpl target = new PaymentServiceImpl(
                mock(PaymentRepository.class),
                mock(MemberCardRepository.class),
                mock(SportPackageRegistrationRepository.class),
                mock(PaymentItemResolver.class),
                mock(BankTransferProperties.class),
                mock(CodeFormatter.class),
                Clock.systemUTC()
        );

        NoTransactionPlatformTransactionManager txManager = new NoTransactionPlatformTransactionManager();
        TransactionInterceptor interceptor = new TransactionInterceptor(txManager, new AnnotationTransactionAttributeSource());

        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setTarget(target);
        proxyFactory.setInterfaces(PaymentService.class);
        proxyFactory.addAdvice(interceptor);

        proxy = (PaymentService) proxyFactory.getProxy();
    }

    @Test
    @DisplayName("createPendingPayment with Propagation.MANDATORY throws IllegalTransactionStateException outside a transaction")
    void createPendingPayment_OutsideTransaction_ThrowsIllegalTransactionStateException() {
        assertThatThrownBy(() -> proxy.createPendingPayment(mock(MemberProfile.class), BigDecimal.valueOf(100000.00), PaymentMethod.BANK_TRANSFER))
                .isInstanceOf(IllegalTransactionStateException.class)
                .hasMessageContaining("No existing transaction found for transaction marked with propagation 'mandatory'");
    }

    @Test
    @DisplayName("markPaymentSuccess with Propagation.MANDATORY throws IllegalTransactionStateException outside a transaction")
    void markPaymentSuccess_OutsideTransaction_ThrowsIllegalTransactionStateException() {
        assertThatThrownBy(() -> proxy.markPaymentSuccess(10L, mock(UserAccount.class)))
                .isInstanceOf(IllegalTransactionStateException.class)
                .hasMessageContaining("No existing transaction found for transaction marked with propagation 'mandatory'");
    }

    @Test
    @DisplayName("markPaymentFailed with Propagation.MANDATORY throws IllegalTransactionStateException outside a transaction")
    void markPaymentFailed_OutsideTransaction_ThrowsIllegalTransactionStateException() {
        assertThatThrownBy(() -> proxy.markPaymentFailed(10L, "Failed reason"))
                .isInstanceOf(IllegalTransactionStateException.class)
                .hasMessageContaining("No existing transaction found for transaction marked with propagation 'mandatory'");
    }

    @Test
    @DisplayName("lockPayment with Propagation.MANDATORY throws IllegalTransactionStateException outside a transaction")
    void lockPayment_OutsideTransaction_ThrowsIllegalTransactionStateException() {
        assertThatThrownBy(() -> proxy.lockPayment(10L))
                .isInstanceOf(IllegalTransactionStateException.class)
                .hasMessageContaining("No existing transaction found for transaction marked with propagation 'mandatory'");
    }
}
