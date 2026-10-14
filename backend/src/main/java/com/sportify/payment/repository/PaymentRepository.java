package com.sportify.payment.repository;

import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByPaymentCode(String paymentCode);

    /**
     * Reads a payment and holds a row lock (SQL Server UPDLOCK, HOLDLOCK) until the transaction ends.
     * The front desk confirm and fail calls start with it, so a second caller waits for the first one to
     * commit and then sees the payment as it is now (no longer PENDING) instead of acting on a stale copy.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    List<Payment> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    List<Payment> findByPaymentStatus(PaymentStatus paymentStatus);

    @Query(value = "SELECT NEXT VALUE FOR seq_payment_code", nativeQuery = true)
    Long getNextPaymentCodeSequence();
}
