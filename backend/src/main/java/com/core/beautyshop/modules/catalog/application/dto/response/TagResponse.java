package com.core.beautyshop.modules.catalog.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
@Schema(description = "Dữ liệu TagResponse")
public class TagResponse {
    @Schema(description = "ID bản ghi")
    private Long id;
    @Schema(description = "Tên hiển thị")
    private String name;
    @Schema(description = "Slug")
    private String slug;
    @Schema(description = "Thời điểm tạo UTC", format = "date-time")
    private Instant createdAt;
    @Schema(description = "Thời điểm cập nhật UTC", format = "date-time")
    private Instant updatedAt;
}
