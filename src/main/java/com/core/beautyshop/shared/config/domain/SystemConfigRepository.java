package com.core.beautyshop.shared.config.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {
    Optional<SystemConfig> findByConfigKeyAndIsDeletedFalse(String configKey);
    List<SystemConfig> findByIsPublicTrueAndIsDeletedFalse();
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT config FROM SystemConfig config WHERE config.configKey = :key")
    Optional<SystemConfig> findByConfigKeyForUpdate(@org.springframework.data.repository.query.Param("key") String key);
}
