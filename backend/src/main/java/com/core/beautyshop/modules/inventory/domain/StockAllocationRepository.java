package com.core.beautyshop.modules.inventory.domain;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;

public interface StockAllocationRepository extends JpaRepository<StockAllocation, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from StockAllocation a where a.orderNumber = :orderNumber and a.variantId = :variantId order by a.stockId")
    List<StockAllocation> lockAllocations(String orderNumber, Long variantId);
}
