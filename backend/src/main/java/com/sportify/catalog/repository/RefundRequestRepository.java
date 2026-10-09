package com.sportify.catalog.repository;

import com.sportify.catalog.entity.RefundRequest;
import com.sportify.catalog.entity.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {
    Optional<RefundRequest> findByRefundCode(String refundCode);
    List<RefundRequest> findByMemberIdOrderByCreatedAtDesc(Long memberId);
    List<RefundRequest> findByStatusOrderByCreatedAtAsc(RefundStatus status);

    @Query(value = "SELECT NEXT VALUE FOR seq_refund_code", nativeQuery = true)
    Long getNextRefundCodeSequence();
}
