package com.sportify.payment.repository;

import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByPaymentCode(String paymentCode);

    List<Payment> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    List<Payment> findByPaymentStatus(PaymentStatus paymentStatus);

    @Query(value = "SELECT NEXT VALUE FOR seq_payment_code", nativeQuery = true)
    Long getNextPaymentCodeSequence();
}
