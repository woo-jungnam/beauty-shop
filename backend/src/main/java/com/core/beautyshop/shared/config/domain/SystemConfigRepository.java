package com.core.beautyshop.shared.config.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {
    Optional<SystemConfig> findByConfigKeyAndIsDeletedFalse(String configKey);
}
