package com.core.beautyshop.modules.order.api;

import com.core.beautyshop.modules.order.application.dto.response.ShipperOrderView;
import com.core.beautyshop.modules.order.application.service.ShipperOrderService;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.dto.PageResponse;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.format.annotation.DateTimeFormat;
import com.core.beautyshop.modules.order.application.dto.response.ShipperStatsView;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Tài xế giao hàng (Shipper)", description = "Các API dành riêng cho shipper: nhận đơn, cập nhật tiến độ, chụp ảnh xác nhận giao hàng")
@RestController
@RequestMapping("/api/v1/shipper")
@RequiredArgsConstructor
public class ShipperOrderController {

    private final ShipperOrderService shipperOrderService;

    @GetMapping("/history")
    public ApiResponse<PageResponse<ShipperOrderView>> getDeliveryHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(PageResponse.of(shipperOrderService.getDeliveryHistory(
                SecurityUtils.getCurrentUserId(), from, to, status, pageable)));
    }

    @GetMapping("/stats")
    public ApiResponse<ShipperStatsView> getStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(shipperOrderService.getShipperStats(SecurityUtils.getCurrentUserId(), from, to));
    }

    @Operation(summary = "Danh sách đơn hàng cần giao của tài xế")
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<PageResponse<ShipperOrderView>>> getDeliveries(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Long targetShipperId = SecurityUtils.getCurrentUserId();
        Page<ShipperOrderView> page = shipperOrderService.getShipperOrders(targetShipperId, status, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page)));
    }

    @Operation(summary = "Chi tiết đơn giao hàng")
    @GetMapping("/orders/{id}")
    public ResponseEntity<ApiResponse<ShipperOrderView>> getOrderDetail(@PathVariable Long id) {
        ShipperOrderView view = shipperOrderService.getShipperOrderDetail(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(view));
    }

    @Operation(summary = "Bắt đầu đi giao hàng (Đang giao)")
    @PatchMapping("/orders/{id}/start")
    public ResponseEntity<ApiResponse<ShipperOrderView>> startDelivery(@PathVariable Long id) {
        ShipperOrderView view = shipperOrderService.startDelivery(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(view));
    }

    public record DeliveryFailureRequest(String reason) {}

    @PostMapping("/orders/{id}/failed")
    public ApiResponse<ShipperOrderView> reportDeliveryFailure(@PathVariable Long id,
            @RequestBody DeliveryFailureRequest request) {
        return ApiResponse.success(shipperOrderService.reportDeliveryFailure(
                id, SecurityUtils.getCurrentUserId(), request.reason()));
    }

    @Operation(summary = "Xác nhận đã giao hàng thành công & tải lên bằng chứng (PoD)")
    @PostMapping(value = "/orders/{id}/delivered", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ShipperOrderView>> completeDelivery(
            @PathVariable Long id,
            @RequestPart("proofImage") MultipartFile proofImage,
            @RequestParam(value = "notes", required = false) String notes
    ) {
        ShipperOrderView view = shipperOrderService.completeDelivery(id, SecurityUtils.getCurrentUserId(), proofImage, notes);
        return ResponseEntity.ok(ApiResponse.success(view));
    }
}

