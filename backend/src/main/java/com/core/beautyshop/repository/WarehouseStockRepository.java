package com.core.beautyshop.repository;

import com.core.beautyshop.entities.warehouse.WarehouseStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface WarehouseStockRepository extends JpaRepository<WarehouseStock, Long> {
    
    // Find stock for a specific variant across all warehouses
    List<WarehouseStock> findByProductVariantId(Long productVariantId);

    // Sum up the available quantity across all warehouses for a specific variant
    @Query("SELECT SUM(ws.quantity - ws.reservedQuantity) FROM WarehouseStock ws WHERE ws.productVariant.id = :variantId")
    Integer getTotalAvailableQuantityForVariant(@Param("variantId") Long variantId);

    List<WarehouseStock> findByWarehouseId(Long warehouseId);
    
    Optional<WarehouseStock> findByWarehouseIdAndProductVariantId(Long warehouseId, Long productVariantId);
}
