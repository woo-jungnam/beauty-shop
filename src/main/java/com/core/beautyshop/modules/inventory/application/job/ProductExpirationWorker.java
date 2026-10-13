package com.core.beautyshop.modules.inventory.application.job;

import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import com.core.beautyshop.modules.inventory.domain.WarehouseStock;
import com.core.beautyshop.modules.inventory.domain.WarehouseStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductExpirationWorker {

    private final WarehouseStockRepository warehouseStockRepository;
    private final CatalogFacade catalogFacade;

    @Transactional
    public void checkVariant(Long variantId) {
        log.info("Starting ProductExpirationJob to check for products nearing expiration...");
        
        LocalDate thirtyDaysFromNow = LocalDate.now().plusDays(30);
        
        List<WarehouseStock> stockedBatches = warehouseStockRepository.findByProductVariantId(variantId).stream()
                .filter(stock -> stock.getQuantity() > 0 && !Boolean.TRUE.equals(stock.getIsDeleted()))
                .filter(stock -> stock.getWarehouse() != null
                        && Boolean.TRUE.equals(stock.getWarehouse().getIsActive())
                        && !Boolean.TRUE.equals(stock.getWarehouse().getIsDeleted()))
                .collect(Collectors.toList());

        LocalDate today = LocalDate.now();
        Map<Long, List<WarehouseStock>> stocksByVariant = stockedBatches.stream()
                .collect(Collectors.groupingBy(WarehouseStock::getProductVariantId));

        for (Map.Entry<Long, List<WarehouseStock>> entry : stocksByVariant.entrySet()) {
            List<WarehouseStock> variantStocks = entry.getValue();
            boolean hasExpiredStock = variantStocks.stream()
                    .anyMatch(stock -> stock.getExpirationDate() != null
                            && !stock.getExpirationDate().isAfter(today));
            boolean hasUnexpiredStock = variantStocks.stream()
                    .anyMatch(stock -> stock.getExpirationDate() == null
                            || stock.getExpirationDate().isAfter(today));
            boolean allUnexpiredStockExpiresSoon = hasUnexpiredStock && variantStocks.stream()
                    .filter(stock -> stock.getExpirationDate() == null
                            || stock.getExpirationDate().isAfter(today))
                    .allMatch(stock -> stock.getExpirationDate() != null
                            && !stock.getExpirationDate().isAfter(thirtyDaysFromNow));

            Optional<ProductVariantSummaryDto> variantOpt = catalogFacade.findVariantSummaryById(entry.getKey());
            if (variantOpt.isPresent()) {
                ProductVariantSummaryDto variant = variantOpt.get();
                if (hasExpiredStock && !hasUnexpiredStock) {
                    if (Boolean.TRUE.equals(variant.getIsActive())) {
                        catalogFacade.deactivateVariant(variant.getId());
                        log.warn("Đã ngưng bán biến thể variantId={} vì không còn lô hàng khả dụng chưa hết hạn",
                                variant.getId());
                    }
                } else if (allUnexpiredStockExpiresSoon) {
                    if (variant.getDiscountPrice() == null && variant.getPrice() != null && Boolean.TRUE.equals(variant.getIsActive())) {
                        BigDecimal discountPrice = variant.getPrice().multiply(new BigDecimal("0.80")).setScale(0, RoundingMode.HALF_UP);
                        catalogFacade.applyDiscountPrice(variant.getId(), discountPrice);
                        log.info("Áp dụng giảm giá thanh lý 20% cho variantId={} (giá mới: {}) vì toàn bộ lô khả dụng đều sắp hết hạn",
                                variant.getId(), discountPrice);
                    }
                } else if (hasUnexpiredStock && variant.getPrice() != null && variant.getDiscountPrice() != null) {
                    BigDecimal automaticDiscount = variant.getPrice().multiply(new BigDecimal("0.80"))
                            .setScale(0, RoundingMode.HALF_UP);
                    if (variant.getDiscountPrice().compareTo(automaticDiscount) == 0) {
                        catalogFacade.applyDiscountPrice(variant.getId(), null);
                        log.info("Removed expiration discount for variantId={} because a longer-dated batch is available",
                                variant.getId());
                    }
                }
            }
        }
        log.info("Finished ProductExpirationJob.");
    }
}
