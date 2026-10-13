package com.core.beautyshop.modules.order.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.dto.PageResponse;
import com.core.beautyshop.modules.order.application.dto.request.UpdateOrderStatusRequest;
import com.core.beautyshop.modules.order.application.dto.request.AdminOrderFilter;
import com.core.beautyshop.modules.order.domain.enums.*;
import java.time.Instant;
import com.core.beautyshop.modules.order.application.dto.response.OrderResponse;
import com.core.beautyshop.modules.order.application.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Quản lý đơn hàng (Admin)", description = "Tra cứu và điều phối đơn hàng cho ADMIN hoặc STAFF; hoàn tiền dùng tuyến ADMIN riêng")
@RestController
@RequestMapping("/api/v1/admin/orders")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    @Operation(summary = "Tìm đơn hàng quản trị", description = "ADMIN/STAFF. Không trả đơn soft-delete; lọc from/to theo createdAt, không phải ngày thực thu. Phân trang mặc định 20, createdAt giảm dần.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getAllOrders(
           @Parameter(description = "Tìm không phân biệt hoa thường trong mã đơn, tên khách hoặc số điện thoại") @RequestParam(required = false) String keyword,
           @RequestParam(required = false) OrderStatus status,
           @RequestParam(required = false) PaymentStatus paymentStatus,
           @Parameter(description = "createdAt từ thời điểm này, gồm mốc; ISO-8601 Instant", example = "2026-10-01T00:00:00Z") @RequestParam(required = false) Instant from,
           @Parameter(description = "createdAt trước thời điểm này, không gồm mốc; ISO-8601 Instant", example = "2026-10-02T00:00:00Z") @RequestParam(required = false) Instant to,
           @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        Page<OrderResponse> page = orderService.searchAdminOrders(new AdminOrderFilter(keyword, status, paymentStatus, from, to), pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page)));
    }

    @Operation(summary = "Xem chi tiết đơn cho ADMIN/ORDER_STAFF", description = "Gồm snapshot hàng hóa/invoice Spa, tiền đã thu/hoàn và lịch sử trạng thái; không cần phiên khách.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable Long id) {
        OrderResponse order = orderService.getAdminOrderById(id);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @Operation(summary = "Điều phối trạng thái đơn cho ADMIN/ORDER_STAFF", description = "PENDING→CONFIRMED/PROCESSING; CONFIRMED→PROCESSING; PROCESSING→SHIPPED→DELIVERED→RETURNED. CANCELLED chỉ từ PENDING/CONFIRMED/PROCESSING và cần notes. BANK phải PAID trước PROCESSING/SHIPPED/DELIVERED. Invoice Spa không nhận đổi trạng thái; gói Spa không dùng giao hàng/trả hàng. Carrier/tracking khi SHIPPED phải cùng trống hoặc cùng có giá trị.")
    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        OrderResponse order = orderService.updateOrderStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(order));
    }
}
