package com.core.beautyshop.shared.config.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {
    Optional<SystemConfig> findByConfigKeyAndIsDeletedFalse(String configKey);
    List<SystemConfig> findByIsPublicTrueAndIsDeletedFalse();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT config FROM SystemConfig config WHERE config.configKey = :key")
    Optional<SystemConfig> findByConfigKeyForUpdate(@Param("key") String key);
}
