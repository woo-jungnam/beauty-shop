package com.core.beautyshop.modules.notification.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface NotificationReceiptRepository extends JpaRepository<NotificationReceipt, String> {

    @Modifying
    @Query("DELETE FROM NotificationReceipt r WHERE r.processedAt < :cutoff")
    int deleteProcessedBefore(@Param("cutoff") Instant cutoff);
}
