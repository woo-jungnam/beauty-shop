package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.modules.spa.application.dto.response.UserServiceTicketResponse;
import com.core.beautyshop.modules.spa.application.dto.request.PurchasePackageRequest;
import com.core.beautyshop.modules.order.api.dto.SpaPackageOrderResult;
import com.core.beautyshop.modules.spa.application.service.SpaTicketService;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Vé liệu trình Spa", description = "API quản lý vé gói dịch vụ và liệu trình Spa của người dùng")
@RestController
@RequestMapping("/api/v1/spa/tickets")
@RequiredArgsConstructor
public class SpaTicketController {

    private final SpaTicketService spaTicketService;

    @Operation(summary = "Xem tất cả vé liệu trình của tôi", description = "Yêu cầu đăng nhập; chỉ vé chưa xóa thuộc tài khoản hiện tại, gồm cả hết hạn/hoàn tất/thu hồi. Danh sách không phân trang.")
    @GetMapping("/my-tickets")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<UserServiceTicketResponse>>> getMyTickets() {
        return ResponseEntity.ok(ApiResponse.success(
                spaTicketService.getMyTickets()
        ));
    }

    @Operation(summary = "Xem vé của tôi còn lượt khả dụng", description = "Chỉ vé chưa xóa, ACTIVE, có orderId, chưa hết hạn và total-used-reserved>0. Booking kiểm lại quyền sở hữu, thanh toán và quota theo dịch vụ; việc có vé trong danh sách không giữ chỗ.")
    @GetMapping("/my-active-tickets")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<UserServiceTicketResponse>>> getMyActiveTickets() {
        return ResponseEntity.ok(ApiResponse.success(
                spaTicketService.getMyActiveTickets()
        ));
    }

    @Operation(summary = "Xem chi tiết một vé liệu trình", description = "Chỉ chủ vé hoặc ADMIN được xem qua endpoint này. STAFF/SPA_RECEPTION có thể đọc lịch sử movement theo phạm vi tương ứng, không tự có quyền API quản trị vé. Không trả vé đã xóa.")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserServiceTicketResponse>> getTicketById(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                spaTicketService.getTicketById(id)
        ));
    }

    @Operation(summary = "Tạo đơn thanh toán mua gói Spa", description = "Tài khoản đăng nhập là người mua/chủ vé. Gói và dịch vụ thành phần phải còn khả dụng, giá ít nhất 1 VND trước làm tròn. Tạo đơn BANK và snapshot quyền lợi/hạn dùng; chỉ cấp vé sau thanh toán thành công. HTTP 201 cho cả tạo mới và replay hợp lệ. Idempotency-Key tùy chọn; cùng key khác nội dung bị từ chối, bỏ key có thể tạo đơn mới khi gửi lại.")
    @PostMapping("/purchase")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SpaPackageOrderResult>> purchasePackage(
            @Parameter(description = "Khóa gửi lại tùy chọn, không để trắng, tối đa 128 ký tự. Giữ nguyên cho cùng yêu cầu mua; nằm trong header, không trong JSON", example = "spa-package-20261005-001") @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PurchasePackageRequest request) {
        request.setIdempotencyKey(idempotencyKey);
        return ResponseEntity.status(201).body(ApiResponse.created(
                spaTicketService.purchasePackage(request),
                "Đã tạo đơn thanh toán gói Spa; vé sẽ được cấp sau khi thanh toán thành công"));
    }

}
