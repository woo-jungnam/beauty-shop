package com.core.beautyshop.modules.spa.domain;

import com.core.beautyshop.modules.spa.domain.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StaffRepository extends JpaRepository<Staff, Long> {
    Optional<Staff> findByUserId(Long userId);
}
