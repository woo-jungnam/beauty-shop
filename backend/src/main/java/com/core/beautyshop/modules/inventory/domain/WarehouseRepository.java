package com.core.beautyshop.modules.inventory.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Warehouse w where w.id = :id")
    Optional<Warehouse> findByIdForUpdate(@Param("id") Long id);

    Optional<Warehouse> findByCodeAndIsDeletedFalse(String code);

    Optional<Warehouse> findByIdAndIsDeletedFalse(Long id);

    List<Warehouse> findByIsActiveTrueAndIsDeletedFalse();

    List<Warehouse> findByIsDeletedFalse();

    boolean existsByCode(String code);
}
