package com.core.beautyshop.modules.inventory.application.job;
import com.core.beautyshop.modules.inventory.domain.WarehouseStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

@Component @RequiredArgsConstructor
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class ProductExpirationJob {
    private final WarehouseStockRepository stocks;
    private final ProductExpirationWorker worker;
    @Scheduled(cron = "${app.cron.product-expiration:0 0 1 * * ?}", zone = "${app.time-zone:Asia/Ho_Chi_Minh}")
    @SchedulerLock(name = "ProductExpiration_check", lockAtMostFor = "30m")
    public void checkAndDiscountExpiringProducts() {
        long cursor = 0;
        while (true) {
            var ids = stocks.findStockedVariantIdsAfter(cursor, PageRequest.of(0, 100));
            if (ids.isEmpty()) return;
            for (Long id : ids) worker.checkVariant(id);
            cursor = ids.getLast();
        }
    }
}
