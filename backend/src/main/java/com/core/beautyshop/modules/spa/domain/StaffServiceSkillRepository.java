package com.core.beautyshop.modules.spa.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StaffServiceSkillRepository extends JpaRepository<StaffServiceSkill, Long> {
    Optional<StaffServiceSkill> findByStaffIdAndServiceIdAndIsDeletedFalse(Long staffId, Long serviceId);
}
