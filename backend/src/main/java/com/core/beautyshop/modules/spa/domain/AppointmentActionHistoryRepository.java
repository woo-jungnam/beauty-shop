package com.core.beautyshop.modules.spa.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;

public interface AppointmentActionHistoryRepository extends JpaRepository<AppointmentActionHistory, Long> {
    Page<AppointmentActionHistory> findByAppointmentIdOrderByIdDesc(Long appointmentId, Pageable pageable);
}
