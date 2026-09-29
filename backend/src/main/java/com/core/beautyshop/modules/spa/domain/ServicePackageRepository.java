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
public interface ServicePackageRepository extends JpaRepository<ServicePackage, Long> {

    @Query("SELECT DISTINCT p FROM ServicePackage p LEFT JOIN FETCH p.items i LEFT JOIN FETCH i.service " +
            "WHERE p.isActive = true AND p.isDeleted = false ORDER BY p.id")
    List<ServicePackage> findAllActiveWithItems();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT servicePackage FROM ServicePackage servicePackage " +
            "WHERE servicePackage.id = :id AND servicePackage.isDeleted = false")
    Optional<ServicePackage> findByIdForUpdateAndIsDeletedFalse(@Param("id") Long id);

    @Query("SELECT DISTINCT p FROM ServicePackage p LEFT JOIN FETCH p.items i LEFT JOIN FETCH i.service WHERE p.isDeleted = false ORDER BY p.id DESC")
    List<ServicePackage> findAllAdminWithItems();
}
