package com.core.beautyshop.modules.inventory.api;

import com.core.beautyshop.modules.inventory.application.service.*;
import com.core.beautyshop.shared.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Sổ cái tồn kho & Cảnh báo (Admin)", description = "API truy vấn lịch sử biến động kho (Ledger) và cảnh báo tồn thấp, cận hạn")
@RestController
@RequestMapping("/api/v1/admin/inventory")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@RequiredArgsConstructor
public class AdminInventoryController {
    private final InventoryLedgerService ledger;
    private final InventoryAlertService alerts;

    @Operation(summary = "Lấy lịch sử biến động kho", description = "ADMIN/INVENTORY_STAFF; thứ tự occurredAt giảm dần. Nếu gửi cả stockId và variantId, stockId được ưu tiên, variantId không được kết hợp lọc. quantityBefore/After là tồn bán được; các movement quarantine/disposal phải đọc cùng type/note.")
    @GetMapping("/transactions")
    public ApiResponse<PageResponse<InventoryLedgerService.LedgerView>> transactions(
            @Parameter(description = "Lọc ID lô tồn, ưu tiên hơn variantId") @RequestParam(required = false) Long stockId,
            @Parameter(description = "Lọc SKU khi không có stockId") @RequestParam(required = false) Long variantId,
            @ParameterObject Pageable pageable) {
        return ApiResponse.success(PageResponse.of(ledger.find(stockId, variantId, pageable)));
    }

    @Operation(summary = "Cảnh báo tồn khả dụng ở mức tối thiểu trở xuống", description = "quantity−reservedQuantity≤minQuantity, gồm cả bằng ngưỡng; lọc lô và kho chưa xóa. quarantine không cộng vào lượng bán được.")
    @GetMapping("/low-stock")
    public ApiResponse<List<InventoryAlertService.StockAlert>> lowStock() {
        return ApiResponse.success(alerts.lowStock());
    }

    @Operation(summary = "Danh sách lô có hạn từ hôm nay đến ngưỡng", description = "Gồm hai mốc LocalDate; không trả lô đã quá hạn trước hôm nay. days từ 0 đến 365, mặc định 30. Chỉ lô và kho chưa xóa; không phải danh sách toàn bộ hàng đã hết hạn.")
    @GetMapping("/expiring-soon")
    public ApiResponse<List<InventoryAlertService.StockAlert>> expiring(@Parameter(description = "Số ngày sau hôm nay, 0–365", schema = @Schema(minimum = "0", maximum = "365", defaultValue = "30")) @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(alerts.expiringSoon(days));
    }
}
