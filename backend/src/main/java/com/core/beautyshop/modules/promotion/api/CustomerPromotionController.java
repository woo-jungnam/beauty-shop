package com.core.beautyshop.modules.promotion.api;

import com.core.beautyshop.modules.promotion.application.VoucherService;
import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Tag(name = "Khuyến mãi & Mã giảm giá Voucher (Khách hàng)", description = "API kiểm tra và áp dụng mã voucher cho khách hàng")
@RestController
@RequestMapping("/api/v1/promotions")
@RequiredArgsConstructor
public class CustomerPromotionController {

    private final VoucherService voucherService;

    @Operation(summary = "Kiểm tra tính hợp lệ và tính số tiền giảm của Voucher")
    @PostMapping("/validate")
    public ApiResponse<VoucherService.VoucherValidationResponse> validateVoucher(
            @jakarta.validation.Valid @RequestBody ValidateVoucherRequest request) {
        Long userId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        VoucherService.VoucherValidationResponse response = voucherService.validateForCustomer(
                request.code(),
                userId,
                request.orderAmount()
        );
        return ApiResponse.success(response);
    }

    @Schema(description = "Yêu cầu kiểm tra mã voucher")
    public record ValidateVoucherRequest(
            @NotBlank(message = "Mã voucher không được để trống")
            @Schema(description = "Mã giảm giá", requiredMode = Schema.RequiredMode.REQUIRED, example = "WELCOME10")
            String code,

            @NotNull(message = "Giá trị đơn hàng không được để trống")
            @Schema(description = "Tổng giá trị đơn hàng trước giảm giá", requiredMode = Schema.RequiredMode.REQUIRED, example = "350000")
            BigDecimal orderAmount
    ) {}
}
