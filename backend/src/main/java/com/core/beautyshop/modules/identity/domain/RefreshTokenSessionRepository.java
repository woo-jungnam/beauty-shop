package com.core.beautyshop.modules.identity.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.time.Instant;

@Repository
public interface RefreshTokenSessionRepository extends JpaRepository<RefreshTokenSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT session FROM RefreshTokenSession session " +
            "WHERE session.tokenHash = :tokenHash AND session.isDeleted = false")
    Optional<RefreshTokenSession> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshTokenSession session SET session.revokedAt = :revokedAt " +
            "WHERE session.familyId = :familyId AND session.revokedAt IS NULL")
    int revokeFamily(@Param("familyId") String familyId, @Param("revokedAt") Instant revokedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshTokenSession session SET session.revokedAt = :revokedAt " +
            "WHERE session.userId = :userId AND session.revokedAt IS NULL")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM RefreshTokenSession session WHERE session.expiresAt < :cutoff OR (session.revokedAt IS NOT NULL AND session.revokedAt < :cutoff)")
    int deleteExpiredOrRevokedBefore(@Param("cutoff") Instant cutoff);
}
