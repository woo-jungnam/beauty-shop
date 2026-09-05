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
