package com.core.beautyshop.modules.inventory.api;

import com.core.beautyshop.modules.inventory.application.service.*;
import com.core.beautyshop.shared.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/inventory")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminInventoryController {
    private final InventoryLedgerService ledger;
    private final InventoryAlertService alerts;

    @GetMapping("/transactions")
    public ApiResponse<PageResponse<InventoryLedgerService.LedgerView>> transactions(
            @RequestParam(required = false) Long stockId, @RequestParam(required = false) Long variantId, Pageable pageable) {
        return ApiResponse.success(PageResponse.of(ledger.find(stockId, variantId, pageable)));
    }
    @GetMapping("/low-stock")
    public ApiResponse<List<InventoryAlertService.StockAlert>> lowStock() { return ApiResponse.success(alerts.lowStock()); }
    @GetMapping("/expiring-soon")
    public ApiResponse<List<InventoryAlertService.StockAlert>> expiring(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(alerts.expiringSoon(days));
    }
}
