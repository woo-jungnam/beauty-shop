package com.core.beautyshop.modules.inventory.application.service;

import com.core.beautyshop.modules.inventory.api.dto.ExpiringVariantStockDto;
import com.core.beautyshop.modules.inventory.domain.WarehouseStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
        Integer quantity = warehouseStockRepository.getTotalAvailableQuantityForVariant(variantId);
        return quantity == null ? 0 : Math.max(0, quantity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpiringVariantStockDto> getExpiringVariantStocks(int thresholdDays, int limit) {
        LocalDate maxDate = LocalDate.now().plusDays(Math.max(1, thresholdDays));
        List<Object[]> rows = warehouseStockRepository.findExpiringVariantStocksRaw(maxDate);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream()
                .limit(Math.max(1, limit))
                .map(row -> ExpiringVariantStockDto.builder()
                        .variantId((Long) row[0])
                        .earliestExpirationDate((LocalDate) row[1])
                        .availableQuantity(row[2] != null ? ((Number) row[2]).intValue() : 0)
                        .build())
                .collect(Collectors.toList());
    }
}
