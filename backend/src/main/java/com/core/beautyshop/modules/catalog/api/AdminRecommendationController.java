package com.core.beautyshop.modules.catalog.api;

import com.core.beautyshop.modules.catalog.infrastructure.RecommendationClient;
import com.core.beautyshop.shared.config.SystemConfigService;
import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name="Quản trị Content-Based", description="Chỉ ADMIN: cấu hình, làm mới danh mục và xem thử điểm cosine/lý do gợi ý")
@RequestMapping("/api/v1/admin/recommendations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminRecommendationController {
    private final RecommendationClient client;
    private final SystemConfigService configs;

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() { return ApiResponse.success(client.status()); }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() { return ApiResponse.success(configs.getRecommendationConfig()); }

    @PutMapping("/config")
    public ApiResponse<Map<String, Object>> update(@RequestBody Map<String, Object> values) {
        Map<String, Object> saved = configs.saveRecommendationConfig(values);
        boolean applied;
        try { applied = "ok".equals(client.refresh().get("status")); }
        catch (BusinessException error) { applied = false; }
        return ApiResponse.success(Map.of("config", saved, "applied", applied), applied
                ? "Đã lưu và áp dụng cấu hình gợi ý" : "Đã lưu cấu hình; hệ thống sẽ đọc lại khi kết nối được dịch vụ gợi ý");
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh() { return ApiResponse.success(client.refresh(), "Đã yêu cầu làm mới dữ liệu gợi ý"); }

    @GetMapping("/preview")
    public ApiResponse<Map<String, Object>> preview(@RequestParam(required=false) Long productId,
            @RequestParam(required=false) Long userId, @RequestParam(required=false) String sessionId,
            @RequestParam(defaultValue="8") int limit) {
        if ((productId != null && productId <= 0) || (userId != null && userId <= 0) || limit < 1 || limit > 50
                || (sessionId != null && sessionId.length() > 100)) throw new BusinessException("Thông tin xem thử gợi ý chưa hợp lệ");
        return ApiResponse.success(client.preview(productId, userId, sessionId, limit));
    }
}
