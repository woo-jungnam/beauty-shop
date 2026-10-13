package com.core.beautyshop.modules.order.api;

import com.core.beautyshop.modules.order.application.dto.request.ConfirmRefundRequest;
import com.core.beautyshop.modules.order.application.service.OrderRefundService;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Xác nhận hoàn tiền đơn hàng (Admin)", description = "API xác nhận hoàn tiền giao dịch dành riêng cho Quản trị viên")
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RefundController {
    private final OrderRefundService refunds;

    @Operation(summary = "Ghi nhận xác nhận đã hoàn tiền thủ công", description = "Chỉ ADMIN. Không gọi ngân hàng để chuyển tiền. Amount phải bằng toàn bộ paidAmount−refundedAmount còn lại và dương; tối đa 2 chữ số thập phân. Đơn thường phải CANCELLED/RETURNED và REFUND_PENDING; invoice Spa COMPLETED có tiền thực thu cần approvalReason. Reference ổn định tối đa 100 ký tự; cùng reference/order/amount gửi lại không tạo bản ghi mới, reference khác đơn hoặc số tiền bị từ chối.")
    @PostMapping("/{id}/refund-confirmation")
    public ApiResponse<String> confirm(@Parameter(description = "ID order/invoice, không phải appointmentId") @PathVariable Long id, @Valid @RequestBody ConfirmRefundRequest request) {
        refunds.confirm(id, request);
        return ApiResponse.success("Refund recorded");
    }
}
