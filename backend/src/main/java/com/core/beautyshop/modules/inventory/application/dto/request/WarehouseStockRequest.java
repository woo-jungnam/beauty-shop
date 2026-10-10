package com.core.beautyshop.modules.inventory.application.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDate;
import java.math.BigDecimal;

@Data
@Schema(description = "Cùng DTO dùng cho đặt tồn tuyệt đối (/stocks) và nhập tăng (/receipts); ý nghĩa quantity phụ thuộc route")
public class WarehouseStockRequest {
    @NotNull(message = "ID biến thể sản phẩm không được để trống")
    @Schema(description = "SKU/product chưa xóa; không yêu cầu còn active để quản lý nội bộ", requiredMode = Schema.RequiredMode.REQUIRED, example = "201")
    private Long productVariantId;

    @NotNull(message = "Số lượng không được để trống")
    @Min(value = 0, message = "Số lượng phải lớn hơn hoặc bằng 0")
    @Schema(description = "/stocks: số tồn sau cập nhật ≥0; /receipts: số nhập cộng thêm >0. Không gồm quarantine", requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", example = "10")
    private Integer quantity;

    @Min(value = 0, message = "Số lượng đặt trước phải lớn hơn hoặc bằng 0")
    @Schema(description = "Không tự tạo reservations. Lô mới phải 0; đặt tồn lô có sẵn phải null hoặc đúng số reserved hiện tại. Receipt giữ số reserved đã có", defaultValue = "0", minimum = "0")
    private Integer reservedQuantity = 0;

    @Schema(description = "Hạn dùng YYYY-MM-DD; receipt nếu có phải sau hôm nay và khớp hạn đã có", example = "2027-10-02")
    private LocalDate expirationDate;

    @Schema(description = "Mã lô trim, tối đa 100 ký tự; null/trắng thành chuỗi rỗng cùng một khóa lô", maxLength = 100, example = "LOT-20261002")
    private String batchCode;

    @Min(0)
    @Schema(description = "Ngưỡng cảnh báo tồn khả dụng; receipt lô đã có giữ cấu hình hiện tại", minimum = "0", defaultValue = "0")
    private Integer minQuantity = 0;
    @Min(0)
    @Schema(description = "Ngưỡng tồn tối đa, tùy chọn; receipt lô đã có giữ cấu hình hiện tại", minimum = "0")
    private Integer maxQuantity;
    @Schema(description = "Vị trí trong kho; receipt lô đã có giữ vị trí hiện tại", example = "A-01")
    private String location;
    @jakarta.validation.constraints.DecimalMin("0")
    @Schema(description = "Giá vốn VND, không âm; mặc định 0. Giá được cập nhật theo request, không tự bình quân", minimum = "0", defaultValue = "0")
    private BigDecimal costPrice = BigDecimal.ZERO;
}
