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
public interface WarehouseStockRepository extends JpaRepository<WarehouseStock, Long> {

    List<WarehouseStock> findByProductVariantId(Long productVariantId);

    List<WarehouseStock> findByProductVariantIdOrderByIdAsc(Long productVariantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ws FROM WarehouseStock ws WHERE ws.productVariantId = :variantId")
    List<WarehouseStock> findByProductVariantIdWithLock(@Param("variantId") Long productVariantId);

    @Query("SELECT SUM(ws.quantity - ws.reservedQuantity) FROM WarehouseStock ws WHERE ws.productVariantId = :variantId")
    Integer getTotalAvailableQuantityForVariant(@Param("variantId") Long variantId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE WarehouseStock ws SET ws.reservedQuantity = ws.reservedQuantity + :quantity " +
           "WHERE ws.id = :id AND (ws.quantity - ws.reservedQuantity) >= :quantity")
    int reserveStockAtomic(@Param("id") Long id, @Param("quantity") int quantity);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE WarehouseStock ws SET ws.reservedQuantity = ws.reservedQuantity - :quantity " +
           "WHERE ws.id = :id AND ws.reservedQuantity >= :quantity")
    int releaseStockAtomic(@Param("id") Long id, @Param("quantity") int quantity);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE WarehouseStock ws SET ws.quantity = ws.quantity - :quantity, ws.reservedQuantity = ws.reservedQuantity - :quantity " +
           "WHERE ws.id = :id AND ws.reservedQuantity >= :quantity")
    int deductStockAtomic(@Param("id") Long id, @Param("quantity") int quantity);

    List<WarehouseStock> findByWarehouseId(Long warehouseId);

    Optional<WarehouseStock> findByWarehouseIdAndProductVariantIdAndBatchCode(Long warehouseId, Long productVariantId, String batchCode);
}
