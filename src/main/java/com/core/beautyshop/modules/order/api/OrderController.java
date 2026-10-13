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
            Tạo đơn hàng sản phẩm từ giỏ của tài khoản đăng nhập hoặc sessionId của khách vãng lai. Chỉ nhận BANK/COD; CASH dành cho invoice Spa.
            BANK có hướng dẫn chuyển khoản khi còn nghĩa vụ thanh toán. Tổng cuối làm tròn VND HALF_UP một lần; BANK tổng 0 được PAID ngay.
            Idempotency-Key tùy chọn: cùng người/phiên, khóa và nội dung trả lại đơn cũ; cùng khóa khác nội dung bị từ chối. Không gửi khóa thì không có bảo đảm gửi lại.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo đơn hàng thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation SYS_003, quy tắc nghiệp vụ SYS_008 (giỏ trống, khóa gửi lại khác nội dung, CASH) hoặc tồn không đủ INV_001")
    })
    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<OrderResponse>> checkout(
            @Parameter(description = "Khóa gửi lại tùy chọn, 1–128 ký tự; giữ nguyên khóa và body khi retry", example = "checkout-20261002-001")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Parameter(description = "Phiên giỏ khách vãng lai; dùng khi body.sessionId trống", example = "guest-session-uuid-12345")
            @RequestHeader(value = "X-Guest-Session-Id", required = false) String guestSessionId,
            @Valid @RequestBody CheckoutRequest request) {
        request.setIdempotencyKey(idempotencyKey);
        if (request.getSessionId() == null || request.getSessionId().isBlank()) {
            request.setSessionId(guestSessionId);
        }
        OrderResponse order = orderService.checkout(request);
        return ResponseEntity.status(201).body(ApiResponse.created(order, "Tạo đơn hàng thành công"));
    }

    @Operation(summary = "Xem chi tiết đơn hàng", description = "Đơn tài khoản: chính chủ hoặc ADMIN. Đơn khách vãng lai: đúng X-Guest-Session-Id hoặc ADMIN. ORDER_STAFF dùng tuyến /api/v1/admin/orders/{id}.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tìm thấy đơn hàng"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không sở hữu đơn hoặc thiếu/sai phiên khách"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng (SYS_004)")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @Parameter(description = "ID đơn hàng", example = "1001") @PathVariable Long id,
            @Parameter(description = "Bắt buộc để xem đơn khách vãng lai khi không có quyền ADMIN; phải khớp phiên lúc checkout")
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

    @Operation(summary = "Lấy tất cả đơn hàng (Admin/Staff)", description = "Dành cho Quản trị viên và Nhân viên theo dõi toàn bộ đơn hàng trong hệ thống.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập (Mã: AUTH_001)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền truy cập (Mã: AUTH_002)")
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getAllOrders(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        Page<OrderResponse> page = orderService.getAllOrders(pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page)));
    }

    @Operation(summary = "Cập nhật trạng thái đơn hàng (Admin/Staff)", description = "PENDING→CONFIRMED/PROCESSING; CONFIRMED→PROCESSING; PROCESSING→SHIPPED→DELIVERED→RETURNED. Có thể CANCELLED từ PENDING/CONFIRMED/PROCESSING và phải có notes. BANK phải PAID trước PROCESSING/SHIPPED/DELIVERED. Invoice Spa không dùng luồng này; đơn gói Spa không SHIPPED/DELIVERED/RETURNED. Carrier và tracking có thể cùng trống, hoặc phải cùng được gửi khi SHIPPED.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cập nhật thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation SYS_003 hoặc vi phạm vòng đời/điều kiện thanh toán SYS_008"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng (SYS_004)")
    })
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @Parameter(description = "ID đơn hàng", example = "1001") @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(orderService.updateOrderStatus(id, request)));
    }

    @Operation(summary = "Hủy đơn hàng", description = "Chính chủ hoặc ADMIN hủy đơn PENDING. Đơn khách vãng lai chỉ ADMIN được hủy qua tuyến này. Giải phóng tồn giữ; tiền đã nhận chuyển REFUND_PENDING, chưa thực hiện hoàn tiền.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Hủy thành công và giải phóng phần tồn kho đã giữ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Đơn không còn PENDING"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập (Mã: AUTH_001)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không sở hữu đơn; chỉ ADMIN hủy được đơn khách vãng lai"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng (SYS_004)")
    })
    @DeleteMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @Parameter(description = "ID đơn hàng cần hủy", example = "1001") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.cancelOrder(id)));
    }
}
