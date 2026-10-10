package com.core.beautyshop.modules.catalog.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu cập nhật quy trình hướng dẫn sử dụng sản phẩm")
public class UpdateProductUsageRequest {

    @NotNull(message = "Danh sách thời điểm dùng không được null")
    @Schema(description = "Các thời điểm khuyên dùng trong ngày hoặc quy trình", example = "[\"Buổi sáng\", \"Buổi tối\", \"Sau bước làm sạch\"]")
    private List<String> whenToUse;

    @NotBlank(message = "Tần suất sử dụng không được để trống")
    @Schema(description = "Tần suất sử dụng", example = "1-2 lần/ngày")
    private String frequency;

    @NotNull(message = "Danh sách các bước thực hiện không được null")
    @Schema(description = "Các bước thao tác sử dụng chi tiết", example = "[\"Làm sạch da bằng sữa rửa mặt\", \"Lấy 3-4 giọt thoa đều lên mặt\", \"Vỗ nhẹ để dưỡng chất thẩm thấu\"]")
    private List<String> instructions;

    @NotNull(message = "Danh sách cảnh báo an toàn không được null")
    @Schema(description = "Cảnh báo và lưu ý an toàn khi sử dụng", example = "[\"Bắt buộc dùng kem chống nắng vào ban ngày\", \"Tránh tiếp xúc trực tiếp với mắt\"]")
    private List<String> warnings;
}
