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
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from WarehouseStock s where s.id = :id")
    Optional<WarehouseStock> findByIdForUpdate(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from WarehouseStock s where s.productVariantId = :variantId and s.isDeleted = false and s.warehouse.isActive = true and s.warehouse.isDeleted = false "
            + "and (s.expirationDate is null or s.expirationDate > CURRENT_DATE) "
            + "order by case when s.expirationDate is null then 1 else 0 end, s.expirationDate, s.id")
    List<WarehouseStock> lockAvailableBatches(Long variantId);

    List<WarehouseStock> findByProductVariantId(Long productVariantId);

    @Query("select distinct s.productVariantId from WarehouseStock s where s.productVariantId > :after "
            + "and s.quantity > 0 and s.isDeleted = false order by s.productVariantId")
    List<Long> findStockedVariantIdsAfter(Long after, org.springframework.data.domain.Pageable pageable);

    List<WarehouseStock> findByProductVariantIdOrderByIdAsc(Long productVariantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ws FROM WarehouseStock ws WHERE ws.productVariantId = :variantId")
    List<WarehouseStock> findByProductVariantIdWithLock(@Param("variantId") Long productVariantId);

    @Query("SELECT SUM(ws.quantity - ws.reservedQuantity) FROM WarehouseStock ws " +
           "WHERE ws.productVariantId = :variantId AND ws.isDeleted = false AND ws.warehouse.isActive = true AND ws.warehouse.isDeleted = false AND (ws.expirationDate IS NULL OR ws.expirationDate > CURRENT_DATE)")
    Integer getTotalAvailableQuantityForVariant(@Param("variantId") Long variantId);

    @Query("SELECT ws FROM WarehouseStock ws " +
           "WHERE ws.productVariantId = :variantId AND ws.isDeleted = false AND ws.warehouse.isActive = true AND ws.warehouse.isDeleted = false AND (ws.expirationDate IS NULL OR ws.expirationDate > CURRENT_DATE) " +
           "ORDER BY CASE WHEN ws.expirationDate IS NULL THEN 1 ELSE 0 END, ws.expirationDate ASC, ws.id ASC")
    List<WarehouseStock> findAvailableStocksByVariantIdFefo(@Param("variantId") Long variantId);

    @Query("SELECT ws FROM WarehouseStock ws WHERE ws.productVariantId = :variantId " +
           "ORDER BY CASE WHEN ws.expirationDate IS NULL THEN 1 ELSE 0 END, ws.expirationDate ASC, ws.id ASC")
    List<WarehouseStock> findStocksByVariantIdFefo(@Param("variantId") Long variantId);

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

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE WarehouseStock ws SET ws.quantity = ws.quantity + :quantity WHERE ws.id = :id")
    int returnStockAtomic(@Param("id") Long id, @Param("quantity") int quantity);

    List<WarehouseStock> findByWarehouseId(Long warehouseId);

    Optional<WarehouseStock> findByWarehouseIdAndProductVariantIdAndBatchCode(Long warehouseId, Long productVariantId, String batchCode);

    @Query("select s from WarehouseStock s join fetch s.warehouse w where s.isDeleted = false and w.isDeleted = false and (s.quantity - s.reservedQuantity) <= s.minQuantity order by (s.quantity - s.reservedQuantity) asc")
    List<WarehouseStock> findLowStock();

    @Query("select s from WarehouseStock s join fetch s.warehouse w where s.isDeleted = false and w.isDeleted = false and s.expirationDate is not null and s.expirationDate between :today and :deadline order by s.expirationDate")
    List<WarehouseStock> findExpiringSoon(@Param("today") java.time.LocalDate today, @Param("deadline") java.time.LocalDate deadline);

    @Query("select coalesce(sum(s.costPrice * s.quantity), 0) from WarehouseStock s where s.isDeleted = false")
    java.math.BigDecimal calculateInventoryValue();
}
