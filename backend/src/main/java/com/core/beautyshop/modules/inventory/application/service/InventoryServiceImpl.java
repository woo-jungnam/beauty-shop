package com.core.beautyshop.modules.inventory.application.service;

import com.core.beautyshop.modules.inventory.api.InventoryExpiryPolicy;
import com.core.beautyshop.modules.inventory.api.dto.ExpiringVariantStockDto;
import com.core.beautyshop.modules.inventory.domain.WarehouseStockRepository;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.time.BusinessTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final WarehouseStockRepository warehouseStockRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean isStockAvailable(Long variantId, int quantity) {
        return quantity > 0 && getAvailableQuantity(variantId) >= quantity;
    }

    @Override
    @Transactional(readOnly = true)
    public int getAvailableQuantity(Long variantId) {
        return getAvailableQuantity(variantId, BusinessTime.today());
    }

    @Override
    @Transactional(readOnly = true)
    public int getAvailableQuantity(Long variantId, LocalDate businessDate) {
        Integer quantity = warehouseStockRepository.getTotalAvailableQuantityForVariant(variantId, InventoryExpiryPolicy.saleCutoff(businessDate));
        return quantity == null ? 0 : Math.max(0, quantity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpiringVariantStockDto> getExpiringVariantStocks(LocalDate businessDate, int thresholdDays, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw new BusinessException("Stock page must be nonnegative and size between 1 and 100");
        LocalDate cutoff = InventoryExpiryPolicy.saleCutoff(businessDate);
        int threshold = Math.max(InventoryExpiryPolicy.MIN_REMAINING_DAYS + 1,
                Math.min(InventoryExpiryPolicy.MAX_CLEARANCE_DAYS, thresholdDays));
        return warehouseStockRepository.findExpiringVariantStocksRaw(cutoff, businessDate.plusDays(threshold), PageRequest.of(page, size))
                .stream().map(row -> ExpiringVariantStockDto.builder()
                        .variantId(((Number) row[0]).longValue())
                        .earliestExpirationDate((LocalDate) row[1])
                        .availableQuantity(row[2] != null ? ((Number) row[2]).intValue() : 0)
                        .build()).toList();
    }
}
