package com.core.beautyshop.shared.config.api;

import com.core.beautyshop.shared.config.SystemConfigService;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Cấu hình công khai (Public System Configs)", description = "API công khai cung cấp các chính sách chung cho Web và Mobile: chính sách Freeship, giờ làm việc Spa...")
@RestController
@RequestMapping("/api/v1/system-configs/public")
@RequiredArgsConstructor
public class PublicSystemConfigController {

    private final SystemConfigService systemConfigService;

    @Operation(summary = "Lấy các cấu hình công khai của hệ thống", description = "Công khai; trả về các chính sách vận hành như ngưỡng miễn phí vận chuyển, phí ship mặc định, giờ mở/đóng cửa Spa.")
    @GetMapping
    public ApiResponse<Map<String, Object>> getPublicConfigs() {
        return ApiResponse.success(systemConfigService.getPublicConfigs());
    }
}
