package com.core.beautyshop.modules.spa.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface FacilityRepository extends JpaRepository<Facility, Long> {
    List<Facility> findByIsDeletedFalseOrderByIdAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Facility f where f.id = :id and f.isDeleted = false")
    Optional<Facility> findForUpdate(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Facility f where f.isDeleted = false order by f.id")
    List<Facility> findAllForAllocationUpdate();
}
