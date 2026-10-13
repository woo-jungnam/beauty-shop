package com.core.beautyshop.modules.inventory.api;

import com.core.beautyshop.modules.inventory.application.service.StockInspectionService;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Kiểm tra hàng cách ly (Admin)", description = "ADMIN/STAFF xử lý hàng trả đang quarantine; kiểm đếm tồn bán được dùng warehouse stock adjustment")
@RestController @RequestMapping("/api/v1/admin/inventory") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
public class StockInspectionController {
    private final StockInspectionService inspection;
    @Schema(description = "Xử lý một phần hàng cách ly; không đặt tồn tuyệt đối và không chống gửi lại")
    public record Inspection(@Schema(description = "Số hàng xử lý, dương và không vượt quarantinedQuantity", requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") @Min(1) int quantity,
                             @Schema(description = "true: chuyển quarantine sang tồn bán được nếu chưa xóa và chưa hết hạn; false: loại bỏ khỏi quarantine", defaultValue = "false") boolean restock) {}

    @Operation(summary = "Duyệt bán lại hoặc loại bỏ hàng cách ly", description = "Trừ quantity khỏi quarantinedQuantity. restock=true cộng vào quantity và ghi ADJUSTMENT; false giữ quantity và ghi DISPOSAL. Lô đã xóa/hết hạn không được restock. API này không phải kiểm kê toàn kho và retry có thể xử lý thêm hàng.")
    @PostMapping("/{stockId}/inspection")
    public ApiResponse<String> inspect(@PathVariable Long stockId, @Valid @RequestBody Inspection request) {
        inspection.inspect(stockId, request.quantity(), request.restock());
        return ApiResponse.success("Inspection recorded");
    }
}
