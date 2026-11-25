package com.core.beautyshop.modules.order.api;

import com.core.beautyshop.modules.order.application.dto.response.ShipperReturnView;
import com.core.beautyshop.modules.order.application.service.ShipperReturnService;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Thu hồi hàng hoàn (Shipper)", description = "Các API cho tài xế nhận nhiệm vụ thu hồi hàng hoàn, chụp ảnh bằng chứng lấy hàng và báo bàn giao về kho")
@RestController
@RequestMapping("/api/v1/shipper/returns")
@RequiredArgsConstructor
public class ShipperReturnController {

    private final ShipperReturnService shipperReturnService;

    public record FailureRequest(String reason) {}

    public record HandoverRequest(String notes) {}

    @Operation(summary = "Danh sách phiếu thu hồi hàng hoàn của tài xế", description = "Liệt kê các yêu cầu trả hàng đã phân công cho tài xế hiện tại theo trạng thái hoặc từ khóa tìm kiếm")
    @GetMapping
    public ApiResponse<PageResponse<ShipperReturnView>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ShipperReturnView> page = shipperReturnService.getShipperReturns(
                SecurityUtils.getCurrentUserId(), status, keyword, pageable);
        return ApiResponse.success(PageResponse.of(page));
    }

    @Operation(summary = "Chi tiết phiếu thu hồi hàng hoàn", description = "Xem chi tiết sản phẩm, địa chỉ thu hồi và bằng chứng khách hàng của phiếu thu hồi thuộc tài xế")
    @GetMapping("/{id}")
    public ApiResponse<ShipperReturnView> detail(@PathVariable Long id) {
        return ApiResponse.success(shipperReturnService.getShipperReturnDetail(id, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "Bắt đầu di chuyển thu hồi hàng hoàn", description = "Ghi nhận tài xế bắt đầu chuyến thu hồi hàng hoàn tới địa chỉ khách hàng")
    @PatchMapping("/{id}/start-pickup")
    public ApiResponse<ShipperReturnView> startPickup(@PathVariable Long id) {
        return ApiResponse.success(shipperReturnService.startPickup(id, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "Xác nhận đã thu hồi hàng hoàn & tải ảnh bằng chứng", description = "Bắt buộc ảnh bằng chứng chụp kiện hàng tại điểm thu hồi; chuyển trạng thái sang PICKED_UP")
    @PostMapping(value = "/{id}/pickup", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ShipperReturnView> completePickup(
            @PathVariable Long id,
            @RequestPart("proofImage") MultipartFile proofImage,
            @RequestParam(value = "notes", required = false) String notes) {
        return ApiResponse.success(shipperReturnService.completePickup(
                id, SecurityUtils.getCurrentUserId(), proofImage, notes));
    }

    @Operation(summary = "Báo thu hồi hàng hoàn thất bại", description = "Ghi nhận lý do không lấy được hàng hoàn và mở lại trạng thái chờ điều phối")
    @PostMapping("/{id}/pickup-failed")
    public ApiResponse<ShipperReturnView> reportFailure(
            @PathVariable Long id,
            @RequestBody FailureRequest request) {
        return ApiResponse.success(shipperReturnService.reportPickupFailure(
                id, SecurityUtils.getCurrentUserId(), request == null ? null : request.reason()));
    }

    @Operation(summary = "Báo đã bàn giao hàng hoàn về kho", description = "Tài xế xác nhận đã nộp hàng hoàn đang giữ (PICKED_UP) về kho để nhân viên kiểm nhận")
    @PostMapping("/{id}/handover")
    public ApiResponse<ShipperReturnView> handover(
            @PathVariable Long id,
            @RequestBody(required = false) HandoverRequest request) {
        return ApiResponse.success(shipperReturnService.requestHandover(
                id, SecurityUtils.getCurrentUserId(), request == null ? null : request.notes()));
    }
}

