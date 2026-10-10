package com.core.beautyshop.modules.catalog.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserProductInteractionRepository extends JpaRepository<UserProductInteraction, Long> {

    @Query("SELECT upi.productId FROM UserProductInteraction upi " +
           "WHERE upi.userId = :userId " +
           "ORDER BY upi.createdAt DESC")
    List<Long> findRecentProductIdsByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT upi.productId FROM UserProductInteraction upi " +
           "WHERE upi.sessionId = :sessionId " +
           "ORDER BY upi.createdAt DESC")
    List<Long> findRecentProductIdsBySessionId(@Param("sessionId") String sessionId, Pageable pageable);
}
