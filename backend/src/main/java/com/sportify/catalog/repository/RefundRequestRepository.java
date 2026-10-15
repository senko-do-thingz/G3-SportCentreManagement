package com.sportify.catalog.repository;

import com.sportify.catalog.entity.RefundRequest;
import com.sportify.catalog.entity.RefundStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {
    Optional<RefundRequest> findByRefundCode(String refundCode);
    List<RefundRequest> findByMemberIdOrderByCreatedAtDesc(Long memberId);
    List<RefundRequest> findByStatusOrderByCreatedAtAsc(RefundStatus status);

    /** True when the payment already has a refund in one of the given statuses (live refunds: PENDING, APPROVED, COMPLETED). */
    boolean existsByPaymentIdAndStatusIn(Long paymentId, Collection<RefundStatus> statuses);

    /**
     * The payment id of a refund, read as a single value so no refund entity is loaded before the payment row is
     * locked (lock order: payment, then refund, then registration). Empty when the refund has no payment (legacy rows).
     */
    @Query("SELECT r.payment.id FROM RefundRequest r WHERE r.id = :refundId")
    Optional<Long> findPaymentIdById(@Param("refundId") Long refundId);

    /** Reads a refund and holds a row lock until the transaction ends, so review and complete run one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefundRequest r WHERE r.id = :refundId")
    Optional<RefundRequest> findByIdForUpdate(@Param("refundId") Long refundId);

    @Query(value = "SELECT NEXT VALUE FOR seq_refund_code", nativeQuery = true)
    Long getNextRefundCodeSequence();
}
