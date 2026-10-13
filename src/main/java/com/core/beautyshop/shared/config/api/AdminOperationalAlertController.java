package com.core.beautyshop.shared.config.api;

import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "Cảnh báo vận hành hệ thống (Admin)", description = "API tổng hợp các chỉ số cảnh báo đơn chờ xử lý, tồn kho thấp, cận hạn, lịch spa và thanh toán bất thường")
@RestController
@RequestMapping("/api/v1/admin/operational-alerts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminOperationalAlertController {
    private final JdbcTemplate jdbc;
    private final com.core.beautyshop.shared.config.SystemConfigService configs;

    @Operation(summary = "Lấy cảnh báo vận hành đang có số lượng", description = "ADMIN. Chỉ trả nhóm có count > 0: đơn cần xử lý, tồn khả dụng thấp theo từng lô, lô gần hạn theo cấu hình, lịch hôm nay chưa hoàn tất/hủy, giao dịch cần đối soát. target là gợi ý màn hình, không phải URL API. Không tự xử lý cảnh báo.")
    @GetMapping
    public ApiResponse<List<AlertView>> alerts() {
        List<AlertView> result = new ArrayList<>();
        add(result, "ORDERS", "Đơn hàng cần xử lý", count("SELECT COUNT(*) FROM orders WHERE is_deleted=false AND status IN ('PENDING','CONFIRMED')"), "warning", "orders");
        add(result, "STOCK", "SKU dưới tồn tối thiểu", count("SELECT COUNT(*) FROM warehouse_stocks WHERE is_deleted=false AND quantity-reserved_quantity<=COALESCE(min_quantity, 0)"), "danger", "inventory");
        int expiryDays = configs.expiryWarningDays();
        add(result, "EXPIRY", "Lô hết hạn trong " + expiryDays + " ngày", count("SELECT COUNT(*) FROM warehouse_stocks WHERE is_deleted=false AND expiration_date BETWEEN CURRENT_DATE AND DATE_ADD(CURRENT_DATE, INTERVAL ? DAY)", expiryDays), "warning", "inventory");
        add(result, "APPOINTMENTS", "Lịch hẹn hôm nay", count("SELECT COUNT(*) FROM appointments WHERE is_deleted=false AND appointment_date=CURRENT_DATE AND status NOT IN ('CANCELLED','COMPLETED')"), "info", "spa");
        add(result, "PAYMENTS", "Thanh toán cần kiểm tra", count("SELECT COUNT(*) FROM payment_transactions WHERE status IN ('FAILED','PARTIALLY_PAID','ORDER_NOT_FOUND')"), "danger", "operations");
        return ApiResponse.success(result);
    }

    private long count(String sql, Object... args) { Long value = jdbc.queryForObject(sql, Long.class, args); return value == null ? 0 : value; }
    private void add(List<AlertView> result, String code, String title, long count, String severity, String target) {
        if (count > 0) result.add(new AlertView(code, title, count, severity, target));
    }
    public record AlertView(String code, String title, long count, String severity, String target) { }
}
