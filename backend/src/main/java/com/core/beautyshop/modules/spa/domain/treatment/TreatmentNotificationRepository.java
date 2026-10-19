package com.core.beautyshop.modules.spa.domain.treatment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TreatmentNotificationRepository extends JpaRepository<TreatmentNotification, Long> {
    boolean existsByEventKey(String eventKey);
    List<TreatmentNotification> findTop100ByUserIdOrderByIdDesc(Long userId);
}
