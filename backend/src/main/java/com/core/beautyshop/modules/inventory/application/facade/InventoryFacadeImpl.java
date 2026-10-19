package com.core.beautyshop.modules.inventory.application.facade;

import com.core.beautyshop.modules.inventory.api.InventoryFacade;
import com.core.beautyshop.modules.inventory.api.InventoryExpiryPolicy;
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

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class InventoryFacadeImpl implements InventoryFacade {

    private final InventoryService inventoryService;
    private final StockAllocationService allocations;
    private final com.core.beautyshop.modules.inventory.application.service.InventoryConsumptionService consumptions;
    private final com.core.beautyshop.modules.inventory.application.service.SpaMaterialOptionsService materialOptions;

    @Override
    public boolean isStockAvailable(Long variantId, int requiredQuantity) {
        return inventoryService.isStockAvailable(variantId, requiredQuantity);
    }

    @Override
    public int getAvailableQuantity(Long variantId) {
        return inventoryService.getAvailableQuantity(variantId);
    }

    @Override
    public int getAvailableQuantity(Long variantId, LocalDate businessDate) {
        return inventoryService.getAvailableQuantity(variantId, businessDate);
    }

    @Override
    public List<ExpiringVariantStockDto> getExpiringVariantStocks(LocalDate businessDate, int thresholdDays, int page, int size) {
        return inventoryService.getExpiringVariantStocks(businessDate, thresholdDays, page, size);
    }

    @Override
    public Predicate hasAvailableStock(CriteriaBuilder builder, CommonAbstractCriteria query, Expression<Long> variantId, LocalDate businessDate) {
        LocalDate cutoff = InventoryExpiryPolicy.saleCutoff(businessDate);
        var available = query.subquery(Long.class);
        var stock = available.from(WarehouseStock.class);
        var total = builder.sumAsLong(builder.diff(stock.<Integer>get("quantity"), stock.<Integer>get("reservedQuantity")));
        available.select(total)
                .where(builder.equal(stock.get("productVariantId"), variantId),
                        builder.isFalse(stock.get("isDeleted")),
                        builder.isFalse(stock.get("isBlocked")),
                        builder.isTrue(stock.get("warehouse").get("isActive")),
                        builder.isFalse(stock.get("warehouse").get("isDeleted")),
                        builder.or(builder.isNull(stock.get("expirationDate")),
                                builder.greaterThan(stock.<LocalDate>get("expirationDate"), cutoff)))
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

    @Override
    public void validateForDispatch(String orderNumber, Long variantId, int quantity) { allocations.validateForDispatch(orderNumber, variantId, quantity); }

    @Override
    public void validateDeliveredAllocation(String orderNumber, Long variantId, int quantity) { allocations.validateDeliveredAllocation(orderNumber, variantId, quantity); }

    @Override
    public void returnPartialStock(String orderNumber, Long variantId, int quantity) { allocations.returnPartial(orderNumber, variantId, quantity); }

    @Override public void replaceAllocation(String orderNumber,Long variantId,int quantity) { allocations.replace(orderNumber,variantId,quantity); }

    @Override
    public com.core.beautyshop.modules.inventory.api.dto.InventoryConsumptionResult consumeSpaUsage(String reference, Long variantId, Long warehouseId, int quantity) {
        return consumptions.consume(reference, variantId, warehouseId, quantity);
    }
    @Override public java.util.List<com.core.beautyshop.modules.inventory.api.dto.SpaMaterialOption> listSpaMaterialOptions() { return materialOptions.list(); }
}
