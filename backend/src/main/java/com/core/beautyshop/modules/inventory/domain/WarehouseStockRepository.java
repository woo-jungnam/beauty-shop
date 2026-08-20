package com.core.beautyshop.modules.inventory.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseStockRepository extends JpaRepository<WarehouseStock, Long> {

    List<WarehouseStock> findByProductVariantId(Long productVariantId);

    @Query("SELECT SUM(ws.quantity - ws.reservedQuantity) FROM WarehouseStock ws WHERE ws.productVariantId = :variantId")
    Integer getTotalAvailableQuantityForVariant(@Param("variantId") Long variantId);

    List<WarehouseStock> findByWarehouseId(Long warehouseId);

    Optional<WarehouseStock> findByWarehouseIdAndProductVariantId(Long warehouseId, Long productVariantId);
}
