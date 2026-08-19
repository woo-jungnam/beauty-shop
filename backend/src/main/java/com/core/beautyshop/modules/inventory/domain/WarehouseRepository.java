package com.core.beautyshop.modules.inventory.domain;

import com.core.beautyshop.modules.inventory.domain.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    Optional<Warehouse> findByCodeAndIsDeletedFalse(String code);

    Optional<Warehouse> findByIdAndIsDeletedFalse(Long id);

    List<Warehouse> findByIsActiveTrueAndIsDeletedFalse();

    List<Warehouse> findByIsDeletedFalse();

    boolean existsByCode(String code);
}
