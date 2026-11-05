package com.core.beautyshop.modules.promotion.api;

import com.core.beautyshop.modules.promotion.application.FlashSaleService;
import com.core.beautyshop.modules.promotion.application.FlashSaleService.CustomerFlashSaleOverview;
import com.core.beautyshop.modules.promotion.application.FlashSaleService.CustomerSlotItem;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Flash Sale Giờ Vàng (Khách hàng)", description = "API công khai xem các mốc giờ Flash Sale và sản phẩm khuyến mãi đang mở bán")
@RestController
@RequestMapping("/api/v1/flash-sales")
@RequiredArgsConstructor
public class CustomerFlashSaleController {

    private final FlashSaleService flashSaleService;

    @Operation(summary = "Lấy tổng quan các khung giờ Flash Sale và sản phẩm đang diễn ra")
    @GetMapping("/current")
    public ApiResponse<CustomerFlashSaleOverview> getCurrentSlot() {
        return ApiResponse.success(flashSaleService.getCustomerFlashSaleOverview());
    }

    @Operation(summary = "Xem danh sách sản phẩm theo ID chiến dịch cụ thể")
    @GetMapping("/{campaignId}/items")
    public ApiResponse<List<CustomerSlotItem>> getCampaignItems(@PathVariable Long campaignId) {
        return ApiResponse.success(flashSaleService.getCampaignItems(campaignId));
    }
}
