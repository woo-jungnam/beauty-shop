package com.core.beautyshop.modules.order.api;
import com.core.beautyshop.modules.order.application.dto.request.ConfirmRefundRequest;
import com.core.beautyshop.modules.order.application.service.OrderRefundService;
import com.core.beautyshop.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RefundController {
    private final OrderRefundService refunds;
    @PostMapping("/{id}/refund-confirmation")
    public ApiResponse<String> confirm(@PathVariable Long id, @Valid @RequestBody ConfirmRefundRequest request) {
        refunds.confirm(id, request);
        return ApiResponse.success("Refund recorded");
    }
}
