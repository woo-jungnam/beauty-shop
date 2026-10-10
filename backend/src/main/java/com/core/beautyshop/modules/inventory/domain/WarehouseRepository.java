package com.core.beautyshop.modules.inventory.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select w from Warehouse w where w.id = :id")
    Optional<Warehouse> findByIdForUpdate(Long id);

    Optional<Warehouse> findByCodeAndIsDeletedFalse(String code);

    Optional<Warehouse> findByIdAndIsDeletedFalse(Long id);

    List<Warehouse> findByIsActiveTrueAndIsDeletedFalse();

    List<Warehouse> findByIsDeletedFalse();

    boolean existsByCode(String code);
}
