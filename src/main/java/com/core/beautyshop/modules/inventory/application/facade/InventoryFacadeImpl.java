package com.core.beautyshop.modules.inventory.application.facade;

import com.core.beautyshop.modules.inventory.api.InventoryFacade;
import com.core.beautyshop.modules.inventory.api.dto.ExpiringVariantStockDto;
import com.core.beautyshop.modules.inventory.application.service.InventoryService;
import com.core.beautyshop.modules.inventory.application.service.StockAllocationService;
import com.core.beautyshop.modules.inventory.domain.StockAllocation.Status;
import com.core.beautyshop.modules.inventory.domain.WarehouseStock;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class InventoryFacadeImpl implements InventoryFacade {

    private final InventoryService inventoryService;
    private final StockAllocationService allocations;

    @Override
    public boolean isStockAvailable(Long variantId, int requiredQuantity) {
        return inventoryService.isStockAvailable(variantId, requiredQuantity);
    }

    @Override
    public int getAvailableQuantity(Long variantId) {
        return inventoryService.getAvailableQuantity(variantId);
    }

    @Override
    public List<ExpiringVariantStockDto> getExpiringVariantStocks(int thresholdDays, int limit) {
        return inventoryService.getExpiringVariantStocks(thresholdDays, limit);
    }

    @Override
    public Predicate hasAvailableStock(CriteriaBuilder builder, CommonAbstractCriteria query, Expression<Long> variantId) {
        var available = query.subquery(Long.class);
        var stock = available.from(WarehouseStock.class);
        var total = builder.sumAsLong(builder.diff(stock.<Integer>get("quantity"), stock.<Integer>get("reservedQuantity")));
        available.select(total)
                .where(builder.equal(stock.get("productVariantId"), variantId),
                        builder.isFalse(stock.get("isDeleted")),
                        builder.isTrue(stock.get("warehouse").get("isActive")),
                        builder.isFalse(stock.get("warehouse").get("isDeleted")),
                        builder.or(builder.isNull(stock.get("expirationDate")),
                                builder.greaterThan(stock.<java.sql.Date>get("expirationDate"), builder.currentDate())))
                .groupBy(stock.get("productVariantId"))
                .having(builder.greaterThan(total, 0L));
        return builder.exists(available);
    }

    @Override
    public void reserveStock(String orderNumber, Long variantId, int quantityToReserve) {
        allocations.reserve(orderNumber, variantId, quantityToReserve);
    }

    @Override
    public void releaseStock(String orderNumber, Long variantId, int quantityToRelease) {
        allocations.transition(orderNumber, variantId, quantityToRelease, Status.RELEASED);
    }

    @Override
    public void deductStock(String orderNumber, Long variantId, int quantityToDeduct) {
        allocations.transition(orderNumber, variantId, quantityToDeduct, Status.DEDUCTED);
    }

    @Override
    public void returnStock(String orderNumber, Long variantId, int quantityToReturn) {
        allocations.transition(orderNumber, variantId, quantityToReturn, Status.QUARANTINED);
    }
}
