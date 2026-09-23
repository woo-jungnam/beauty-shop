package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.modules.spa.application.dto.response.UserServiceTicketResponse;
import com.core.beautyshop.modules.spa.application.dto.request.PurchasePackageRequest;
import com.core.beautyshop.modules.order.api.dto.SpaPackageOrderResult;
import com.core.beautyshop.modules.spa.application.service.SpaTicketService;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "Xem tất cả vé liệu trình của tôi")
    @GetMapping("/my-tickets")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<UserServiceTicketResponse>>> getMyTickets() {
        return ResponseEntity.ok(ApiResponse.success(
                spaTicketService.getMyTickets()
        ));
    }

    @Operation(summary = "Xem danh sách vé liệu trình còn hiệu lực của tôi")
    @GetMapping("/my-active-tickets")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<UserServiceTicketResponse>>> getMyActiveTickets() {
        return ResponseEntity.ok(ApiResponse.success(
                spaTicketService.getMyActiveTickets()
        ));
    }

    @Operation(summary = "Xem chi tiết một vé liệu trình")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserServiceTicketResponse>> getTicketById(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                spaTicketService.getTicketById(id)
        ));
    }

    @Operation(summary = "Tạo đơn thanh toán mua gói dịch vụ Spa")
    @PostMapping("/purchase")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SpaPackageOrderResult>> purchasePackage(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PurchasePackageRequest request) {
        request.setIdempotencyKey(idempotencyKey);
        return ResponseEntity.status(201).body(ApiResponse.created(
                spaTicketService.purchasePackage(request),
                "Đã tạo đơn thanh toán gói Spa; vé sẽ được cấp sau khi thanh toán thành công"));
    }

}
