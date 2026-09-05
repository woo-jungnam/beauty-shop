package com.core.beautyshop.modules.inventory.application.job;

import com.core.beautyshop.modules.catalog.domain.ProductVariant;
import com.core.beautyshop.modules.catalog.domain.ProductVariantRepository;
import com.core.beautyshop.modules.inventory.domain.WarehouseStock;
import com.core.beautyshop.modules.inventory.domain.WarehouseStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductExpirationJob {

    private final WarehouseStockRepository warehouseStockRepository;
    private final ProductVariantRepository productVariantRepository;

    @Scheduled(cron = "0 0 1 * * ?") // Run at 1 AM every day
    @Transactional
    public void checkAndDiscountExpiringProducts() {
        log.info("Starting ProductExpirationJob to check for products nearing expiration...");
        
        LocalDate thirtyDaysFromNow = LocalDate.now().plusDays(30);
        
        // Find all stock expiring before 30 days from now, and where quantity > 0
        List<WarehouseStock> expiringStocks = warehouseStockRepository.findAll().stream()
                .filter(stock -> stock.getExpirationDate() != null)
                .filter(stock -> !stock.getExpirationDate().isAfter(thirtyDaysFromNow))
                .filter(stock -> stock.getQuantity() > 0)
                .collect(Collectors.toList());

        for (WarehouseStock stock : expiringStocks) {
            ProductVariant variant = productVariantRepository.findById(stock.getProductVariantId()).orElse(null);
            if (variant != null && variant.getDiscountPrice() == null) {
                // Apply a 20% discount if not already discounted
                BigDecimal discountPrice = variant.getPrice().multiply(new BigDecimal("0.8"));
                variant.setDiscountPrice(discountPrice);
                productVariantRepository.save(variant);
                log.info("Applied 20% discount to ProductVariant ID: {} due to expiring stock ID: {}", variant.getId(), stock.getId());
            } else if (stock.getExpirationDate().isBefore(LocalDate.now())) {
                // If actually expired, we should perhaps mark it inactive or alert
                if (variant != null && Boolean.TRUE.equals(variant.getIsActive())) {
                    variant.setIsActive(false);
                    productVariantRepository.save(variant);
                    log.info("Deactivated ProductVariant ID: {} due to expired stock ID: {}", variant.getId(), stock.getId());
                }
            }
        }
        log.info("Finished ProductExpirationJob.");
    }
}
