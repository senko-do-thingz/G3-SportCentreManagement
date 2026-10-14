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
     * Lock order rule: every path that changes a payment and the card or registration it pays for takes this
     * lock first and only then reads and writes the card or registration (confirm, fail, the card and package
     * activate and cancel endpoints). A second caller waits here holding no other lock, so paths that lock one
     * payment and then its own card or registration cannot deadlock with each other, and the waiter then sees the
     * payment as it is now instead of acting on a stale copy. The known remaining case (an upgrade activation that
     * also replaces the member's current card, with two different payments of the same member) is documented in
     * docs/database/09.
     * Calling it again in the same transaction is harmless: the transaction already owns the lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    List<Payment> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    List<Payment> findByPaymentStatus(PaymentStatus paymentStatus);

    @Query(value = "SELECT NEXT VALUE FOR seq_payment_code", nativeQuery = true)
    Long getNextPaymentCodeSequence();
}
