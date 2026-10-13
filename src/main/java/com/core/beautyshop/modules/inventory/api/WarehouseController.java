package com.core.beautyshop.modules.inventory.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.modules.inventory.application.dto.request.CreateWarehouseRequest;
import com.core.beautyshop.modules.inventory.application.dto.request.UpdateWarehouseRequest;
import com.core.beautyshop.modules.inventory.application.dto.request.WarehouseStockRequest;
import com.core.beautyshop.modules.inventory.application.dto.response.WarehouseResponse;
import com.core.beautyshop.modules.inventory.application.dto.response.WarehouseStockResponse;
import com.core.beautyshop.modules.inventory.application.service.WarehouseService;
import com.core.beautyshop.modules.inventory.application.service.WarehouseStockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Quản lý kho", description = "ADMIN/STAFF quản lý kho, lô hàng, nhập tăng tồn, điều chỉnh tuyệt đối và điều chuyển")
@RestController
@RequestMapping("/api/v1/admin/warehouses")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;
    private final WarehouseStockService warehouseStockService;
    private final com.core.beautyshop.modules.inventory.application.service.InventoryMovementService movementService;

    @Operation(summary = "Lấy danh sách kho chưa xóa", description = "Gồm kho active và inactive; không phân trang.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<WarehouseResponse>>> getAllWarehouses() {
        List<WarehouseResponse> warehouses = warehouseService.getAllWarehouses();
        return ResponseEntity.ok(ApiResponse.success(warehouses));
    }

    @Operation(summary = "Xem thông tin kho bãi theo ID (Admin)")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WarehouseResponse>> getWarehouseById(@PathVariable Long id) {
        WarehouseResponse warehouse = warehouseService.getWarehouseById(id);
        return ResponseEntity.ok(ApiResponse.success(warehouse));
    }

    @Operation(summary = "Tạo kho bãi mới (Admin)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Đã tạo kho; mặc định loại BRANCH khi không gửi warehouseType")
    @PostMapping
    public ResponseEntity<ApiResponse<WarehouseResponse>> createWarehouse(
            @Valid @RequestBody CreateWarehouseRequest request) {
        WarehouseResponse warehouse = warehouseService.createWarehouse(request);
        return ResponseEntity.status(201).body(ApiResponse.created(warehouse, "Tạo kho bãi thành công"));
    }

    @Operation(summary = "Cập nhật thông tin kho", description = "Chỉ cập nhật các trường khác null; mã code không đổi. isActive=false ngừng sử dụng kho cho nhập/điều chuyển mới.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WarehouseResponse>> updateWarehouse(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWarehouseRequest request) {
        WarehouseResponse warehouse = warehouseService.updateWarehouse(id, request);
        return ResponseEntity.ok(ApiResponse.success(warehouse));
    }

    @Operation(summary = "Xóa mềm kho khi đã hết số dư", description = "Chặn nếu bất kỳ lô nào còn quantity/reservedQuantity/quarantinedQuantity khác 0, kể cả lô legacy đã soft-delete. Khóa kho và lô để kiểm tra; không xóa lịch sử hay tự xử lý tồn. Cần điều chuyển/xử lý/đối soát hết số dư trước.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteWarehouse(@PathVariable Long id) {
        warehouseService.deleteWarehouse(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Lấy danh sách tồn kho theo kho bãi (Admin)")
    @GetMapping("/{id}/stocks")
    public ResponseEntity<ApiResponse<List<WarehouseStockResponse>>> getWarehouseStocks(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                warehouseStockService.getStocksByWarehouseId(id)));
    }

    @Operation(summary = "Đặt số lượng tuyệt đối và thông tin lô", description = "quantity là số tồn bán được sau cập nhật, không phải lượng cộng thêm. Khóa lô theo warehouse/SKU/batchCode; null hoặc trắng batchCode được chuẩn hóa rỗng. Không cho sửa reservations do checkout hoặc đặt quantity dưới số reserved. SKU/product chưa xóa được nhận dù ngưng bán. Dùng /receipts để nhập tăng tồn.")
    @PostMapping("/{id}/stocks")
    public ResponseEntity<ApiResponse<WarehouseStockResponse>> addOrUpdateStock(
            @PathVariable Long id,
            @Valid @RequestBody WarehouseStockRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                warehouseStockService.addOrUpdateStock(id, request)));
    }

    @Operation(summary = "Nhập tăng số lượng một lô", description = "quantity>0 được cộng vào tồn hiện tại; bảo toàn reserved/quarantine và cấu hình min/max/location của lô đã có. Hạn dùng phải sau hôm nay và không được thay hạn đã có sang giá trị khác. Không tự chống gửi lại: retry request có thể nhập thêm lần nữa; phiếu mua dùng /procurement/orders/{id}/receive để kiểm soát vòng đời.")
    @PostMapping("/{id}/receipts")
    public ResponseEntity<ApiResponse<WarehouseStockResponse>> receiveStock(
            @PathVariable Long id, @Valid @RequestBody WarehouseStockRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                warehouseStockService.receiveStock(id, request, "MANUAL_RECEIPT", null)));
    }

    @Operation(summary = "Xóa mềm lô tồn kho", description = "Chặn khi còn reserved hoặc quarantine. Tồn bán được còn lại ghi DISPOSAL rồi đưa về 0; không phải chỉ ẩn dòng.")
    @DeleteMapping("/stocks/{stockId}")
    public ResponseEntity<ApiResponse<Void>> deleteStock(@PathVariable Long stockId) {
        warehouseStockService.deleteStock(stockId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Điều chỉnh số tồn bán được sau kiểm đếm", description = "quantityAfter là số tuyệt đối, phải ≥reservedQuantity; cần reason không trống. Chênh lệch ghi ADJUSTMENT; quarantine tách riêng. Cùng số lượng hiện tại không tạo movement.")
    @PutMapping("/stocks/{stockId}/adjustment")
    public ResponseEntity<ApiResponse<Void>> adjust(@PathVariable Long stockId, @RequestBody AdjustmentRequest request) {
        movementService.adjust(stockId, request.quantityAfter(), request.reason());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Điều chuyển tồn khả dụng sang kho khác", description = "quantity>0 và ≤quantity−reservedQuantity; kho nguồn/đích phải khác, active và chưa xóa. Giữ SKU, batchCode và hạn dùng; không chuyển reservations hoặc quarantine. Cặp lô đích đã có phải khớp hạn dùng. Đây là cộng/trừ tồn, không có Idempotency-Key; retry có thể chuyển tiếp.")
    @PostMapping("/stocks/{stockId}/transfer")
    public ResponseEntity<ApiResponse<Void>> transfer(@PathVariable Long stockId, @RequestBody TransferRequest request) {
        movementService.transfer(stockId, request.targetWarehouseId(), request.quantity(), request.reason());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
    @Schema(description = "Điều chỉnh tuyệt đối; kiểm tra nghiệp vụ tại service")
    public record AdjustmentRequest(@Schema(description = "Tồn bán được sau điều chỉnh, không gồm quarantine; không thấp hơn reserved. Bỏ trường này mặc định 0", minimum = "0", defaultValue = "0") int quantityAfter,
                                    @Schema(description = "Lý do bắt buộc, không trống", requiredMode = Schema.RequiredMode.REQUIRED) String reason) { }
    @Schema(description = "Điều chuyển phần tồn chưa giữ; không tự chống retry")
    public record TransferRequest(@Schema(description = "ID kho đích active, khác kho nguồn", requiredMode = Schema.RequiredMode.REQUIRED) Long targetWarehouseId,
                                  @Schema(description = "Lượng chuyển dương, không vượt tồn khả dụng", requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") int quantity,
                                  @Schema(description = "Ghi chú điều chuyển, tùy chọn") String reason) { }
}
