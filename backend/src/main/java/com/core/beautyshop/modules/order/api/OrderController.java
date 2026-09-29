package com.core.beautyshop.modules.order.api;

import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.dto.PageResponse;
import com.core.beautyshop.modules.order.application.dto.request.CheckoutRequest;
import com.core.beautyshop.modules.order.application.dto.request.UpdateOrderStatusRequest;
import com.core.beautyshop.modules.order.application.dto.response.OrderResponse;
import com.core.beautyshop.modules.order.application.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Quản lý đơn hàng & Checkout", description = "Các API thanh toán, tạo đơn hàng, tra cứu lịch sử và cập nhật trạng thái đơn")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "Thanh toán & tạo đơn hàng mới", description = """
            Tạo đơn hàng từ giỏ hàng hiện tại (dựa trên token đăng nhập hoặc sessionId cho khách vãng lai).
            Nếu chọn BANK, trường paymentInstruction trong phản hồi sẽ chứa thông tin tài khoản và mã VietQR SePay để hiển thị cho khách quét.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo đơn hàng thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ (Mã: SYS_003) HOẶC giỏ hàng trống (Mã: ORD_001) HOẶC không đủ tồn kho (Mã: INV_001)")
    })
    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<OrderResponse>> checkout(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Guest-Session-Id", required = false) String guestSessionId,
            @Valid @RequestBody CheckoutRequest request) {
        request.setIdempotencyKey(idempotencyKey);
        if (request.getSessionId() == null || request.getSessionId().isBlank()) {
            request.setSessionId(guestSessionId);
        }
        OrderResponse order = orderService.checkout(request);
        return ResponseEntity.status(201).body(ApiResponse.created(order, "Tạo đơn hàng thành công"));
    }

    @Operation(summary = "Xem chi tiết đơn hàng", description = "Tra cứu thông tin chi tiết của một đơn hàng theo ID.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tìm thấy đơn hàng"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng (Mã: ORD_002 / SYS_004)")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @Parameter(description = "ID đơn hàng", example = "1001") @PathVariable Long id,
            @RequestHeader(value = "X-Guest-Session-Id", required = false) String guestSessionId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderById(id, guestSessionId)));
    }

    @Operation(summary = "Xem danh sách đơn hàng của tôi", description = "Lấy lịch sử đơn hàng của người dùng đang đăng nhập. Yêu cầu Bearer Token.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập (Mã: AUTH_001)")
    })
    @GetMapping("/my-orders")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getMyOrders(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        Page<OrderResponse> page = orderService.getMyOrders(pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page)));
    }

    @Operation(summary = "Xem đơn hàng theo User ID (Admin hoặc chính chủ)", description = "Lấy danh sách đơn hàng của một người dùng cụ thể.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập (Mã: AUTH_001)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền xem đơn hàng của người khác (Mã: AUTH_002)")
    })
    @GetMapping("/user/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getOrdersByUser(
            @Parameter(description = "ID người dùng", example = "1") @PathVariable Long userId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        Page<OrderResponse> page = orderService.getOrdersByUser(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page)));
    }

    @Operation(summary = "Lấy tất cả đơn hàng (Admin)", description = "Dành cho Quản trị viên theo dõi toàn bộ đơn hàng trong hệ thống.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập (Mã: AUTH_001)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền ADMIN (Mã: AUTH_002)")
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getAllOrders(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        Page<OrderResponse> page = orderService.getAllOrders(pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page)));
    }

    @Operation(summary = "Cập nhật trạng thái đơn hàng (Admin)", description = "Chuyển đổi trạng thái đơn hàng (ví dụ: CONFIRMED -> SHIPPED -> DELIVERED). Yêu cầu quyền ADMIN.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cập nhật thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ hoặc trạng thái chuyển tiếp không hợp lệ (Mã: ORD_004)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng (Mã: ORD_002)")
    })
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @Parameter(description = "ID đơn hàng", example = "1001") @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(orderService.updateOrderStatus(id, request)));
    }

    @Operation(summary = "Hủy đơn hàng", description = "Khách hàng hủy đơn hàng của mình. Chỉ được phép hủy khi đơn hàng đang ở trạng thái PENDING hoặc CONFIRMED.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Hủy đơn hàng thành công và hoàn trả tồn kho"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Đơn hàng đã được giao hoặc đang vận chuyển nên không thể hủy (Mã: ORD_003)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập (Mã: AUTH_001)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng (Mã: ORD_002)")
    })
    @DeleteMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @Parameter(description = "ID đơn hàng cần hủy", example = "1001") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.cancelOrder(id)));
    }
}
