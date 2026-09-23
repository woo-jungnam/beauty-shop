package com.core.beautyshop.shared.outbox.domain;

import com.core.beautyshop.shared.outbox.domain.enums.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    @Query("SELECT m FROM OutboxMessage m WHERE m.status = 'PENDING' AND m.retryCount < 5 ORDER BY m.createdAt ASC")
    List<OutboxMessage> findPendingMessages(Pageable pageable);

    @Query("select m from OutboxMessage m where (m.status = 'PENDING' or (m.status = 'PROCESSING' and m.leaseUntil < :now)) "
            + "and m.retryCount < 5 and not exists (select earlier.id from OutboxMessage earlier "
            + "where earlier.aggregateType = m.aggregateType and earlier.aggregateId = m.aggregateId "
            + "and earlier.id < m.id and earlier.status <> 'PUBLISHED') order by m.id")
    List<OutboxMessage> findClaimable(java.time.LocalDateTime now, Pageable pageable);

    @Modifying
    @Query("update OutboxMessage m set m.status = 'PROCESSING', m.claimToken = :token, m.leaseUntil = :until "
            + "where m.id = :id and (m.status = 'PENDING' or (m.status = 'PROCESSING' and m.leaseUntil < :now))")
    int claim(Long id, String token, java.time.LocalDateTime until, java.time.LocalDateTime now);

    @Modifying
    @Query("update OutboxMessage m set m.status = 'PUBLISHED', m.sentAt = :now, m.claimToken = null, m.leaseUntil = null "
            + "where m.id = :id and m.claimToken = :token and m.status = 'PROCESSING'")
    int completeClaim(Long id, String token, java.time.LocalDateTime now);

    @Modifying
    @Query("update OutboxMessage m set m.status = :status, m.retryCount = m.retryCount + 1, m.errorMessage = :error, "
            + "m.claimToken = null, m.leaseUntil = null where m.id = :id and m.claimToken = :token and m.status = 'PROCESSING'")
    int failClaim(Long id, String token, OutboxStatus status, String error);

    @Modifying
    @Query("UPDATE OutboxMessage m SET m.status = :status, m.sentAt = :sentAt WHERE m.id = :id")
    int markAsPublished(@Param("id") Long id, @Param("status") OutboxStatus status, @Param("sentAt") LocalDateTime sentAt);

    @Modifying
    @Query("UPDATE OutboxMessage m SET m.retryCount = m.retryCount + 1, m.errorMessage = :errorMessage WHERE m.id = :id")
    int incrementRetryCount(@Param("id") Long id, @Param("errorMessage") String errorMessage);

    @Modifying
    @Query("UPDATE OutboxMessage m SET m.status = 'FAILED', m.errorMessage = :errorMessage WHERE m.id = :id")
    int markAsFailed(@Param("id") Long id, @Param("errorMessage") String errorMessage);
}
