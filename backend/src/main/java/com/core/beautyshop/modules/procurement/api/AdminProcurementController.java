package com.core.beautyshop.modules.procurement.api;

import com.core.beautyshop.modules.procurement.application.ProcurementService;
import com.core.beautyshop.modules.procurement.domain.PurchaseOrder;
import com.core.beautyshop.shared.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Nhà cung cấp & Mua hàng (Admin)", description = "ADMIN/STAFF quản lý nhà cung cấp và phiếu DRAFT→APPROVED→RECEIVED; receive nhận toàn bộ số lượng còn lại")
@RestController @RequestMapping("/api/v1/admin/procurement")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')") @RequiredArgsConstructor
public class AdminProcurementController {
    private final ProcurementService service;

    @Operation(summary = "Lấy danh sách nhà cung cấp (phân trang)")
    @GetMapping("/suppliers")
    public ApiResponse<PageResponse<ProcurementService.SupplierView>> suppliers(@ParameterObject Pageable pageable) {
        return ApiResponse.success(PageResponse.of(service.suppliers(pageable)));
    }

    @Operation(summary = "Xem chi tiết nhà cung cấp theo ID")
    @GetMapping("/suppliers/{id}")
    public ApiResponse<ProcurementService.SupplierView> getSupplier(@PathVariable Long id) {
        return ApiResponse.success(service.supplier(id));
    }

    @Operation(summary = "Tạo nhà cung cấp", description = "code/name bắt buộc; code trim và viết hoa, duy nhất không phân biệt hoa thường. active mặc định true. Không có Idempotency-Key. HTTP 200; trường status trong ApiResponse.created là 201.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "HTTP 200; body ApiResponse.status=201 và data là nhà cung cấp mới")
    @PostMapping("/suppliers")
    public ApiResponse<ProcurementService.SupplierView> createSupplier(@RequestBody ProcurementService.SupplierCommand command) {
        return ApiResponse.created(service.saveSupplier(null, command), "Supplier created");
    }

    @Operation(summary = "Thay thông tin nhà cung cấp", description = "Cần gửi lại code/name; các trường nullable được thay theo body, không phải patch. active thiếu/null được đặt true.")
    @PutMapping("/suppliers/{id}")
    public ApiResponse<ProcurementService.SupplierView> updateSupplier(@PathVariable Long id, @RequestBody ProcurementService.SupplierCommand command) {
        return ApiResponse.success(service.saveSupplier(id, command));
    }

    @Operation(summary = "Xóa nhà cung cấp")
    @DeleteMapping("/suppliers/{id}")
    public ApiResponse<Void> deleteSupplier(@PathVariable Long id) {
        service.deleteSupplier(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "Lấy danh sách phiếu mua hàng (lọc theo trạng thái, phân trang)")
    @GetMapping("/orders")
    public ApiResponse<PageResponse<ProcurementService.PurchaseOrderView>> orders(@Parameter(description = "Trạng thái DRAFT, APPROVED, RECEIVED hoặc CANCELLED") @RequestParam(required=false) PurchaseOrder.Status status, @ParameterObject Pageable pageable) {
        return ApiResponse.success(PageResponse.of(service.orders(status, pageable)));
    }

    @Operation(summary = "Xem chi tiết phiếu mua hàng theo ID")
    @GetMapping("/orders/{id}")
    public ApiResponse<ProcurementService.PurchaseOrderView> getOrder(@PathVariable Long id) {
        return ApiResponse.success(service.order(id));
    }

    @Operation(summary = "Tạo phiếu mua DRAFT", description = "Cần supplier active/chưa xóa, kho active/chưa xóa, items không rỗng. SKU/product chưa xóa được mua dù ngưng bán. Mỗi dòng quantity>0, unitCost≥0 tối đa 2 số lẻ; hạn dùng nếu có phải sau hôm nay. Tổng=sum(quantity×unitCost). Không có Idempotency-Key; retry tạo phiếu mới. HTTP 200; body ApiResponse.status=201.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "HTTP 200; body ApiResponse.status=201 và phiếu DRAFT mới")
    @PostMapping("/orders")
    public ApiResponse<ProcurementService.PurchaseOrderView> createOrder(@RequestBody ProcurementService.OrderCommand command) {
        return ApiResponse.created(service.create(command), "Purchase order created");
    }

    @Operation(summary = "Thay nội dung phiếu DRAFT", description = "Chỉ DRAFT; thay toàn bộ items, supplier, warehouse, expectedDate/note và tính lại tổng. Kiểm tham chiếu/quantity/cost/hạn dùng như tạo mới; không sửa phiếu APPROVED/RECEIVED/CANCELLED.")
    @PutMapping("/orders/{id}")
    public ApiResponse<ProcurementService.PurchaseOrderView> updateOrder(@PathVariable Long id,
                                                                         @RequestBody ProcurementService.OrderCommand command) {
        return ApiResponse.success(service.update(id, command));
    }

    @Operation(summary = "Phê duyệt DRAFT→APPROVED", description = "Kiểm lại supplier active, kho active và SKU chưa xóa; chưa nhập tồn. Gọi lại khi đã APPROVED bị từ chối, không phải endpoint idempotent.")
    @PutMapping("/orders/{id}/approve")
    public ApiResponse<ProcurementService.PurchaseOrderView> approve(@PathVariable Long id) {
        return ApiResponse.success(service.approve(id));
    }

    @Operation(summary = "Nhận toàn bộ hàng APPROVED→RECEIVED", description = "Không có body. Cộng phần quantity−receivedQuantity của mọi dòng vào lô trong cùng transaction, ghi ledger PURCHASE_ORDER và cập nhật receivedQuantity. Chưa hỗ trợ nhập một phần hoặc chọn lượng/hạn tại lúc nhận. Gọi lại khi RECEIVED bị từ chối và không nhập thêm.")
    @PutMapping("/orders/{id}/receive")
    public ApiResponse<ProcurementService.PurchaseOrderView> receive(@PathVariable Long id) {
        return ApiResponse.success(service.receive(id));
    }

    @Operation(summary = "Hủy phiếu chưa nhận", description = "Cho DRAFT/APPROVED/CANCELLED; RECEIVED bị từ chối. Không đảo tồn kho đã nhận.")
    @PutMapping("/orders/{id}/cancel")
    public ApiResponse<ProcurementService.PurchaseOrderView> cancel(@PathVariable Long id) {
        return ApiResponse.success(service.cancel(id));
    }
}
