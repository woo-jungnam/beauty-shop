package com.core.beautyshop.modules.spa.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface StaffRepository extends JpaRepository<Staff, Long> {
    Optional<Staff> findByUserId(Long userId);

    @Query("SELECT DISTINCT s FROM Staff s JOIN FETCH s.skills skill JOIN FETCH skill.service service " +
            "WHERE service.id = :serviceId AND s.isActive = true AND s.isDeleted = false " +
            "AND skill.isDeleted = false AND service.isActive = true AND service.isDeleted = false")
    List<Staff> findQualifiedActiveStaff(@Param("serviceId") Long serviceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Staff s WHERE s.id = :id")
    Optional<Staff> findByIdWithLock(@Param("id") Long id);

    @Query("select distinct s from Staff s left join fetch s.skills skill left join fetch skill.service where s.isDeleted = false order by s.id desc")
    List<Staff> findAllAdminWithSkills();
}
