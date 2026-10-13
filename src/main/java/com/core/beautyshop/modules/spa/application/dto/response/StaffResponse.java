package com.core.beautyshop.modules.spa.application.dto.response;

import com.core.beautyshop.modules.spa.domain.Staff;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Nhân viên có kỹ năng dịch vụ; không chứng minh ca/slot còn trống")
public class StaffResponse {
    @Schema(description = "ID hồ sơ Staff; dùng trong staffId của booking/assignment")
    private Long id;
    @Schema(description = "ID tài khoản User liên kết, dùng để xác định người thực hiện được giao")
    private Long userId;
    private String fullName;
    private String specialty;
    private Double rating;
    private Integer totalReviews;

    public static StaffResponse of(Staff entity, String fullName) {
        if (entity == null) return null;
        return StaffResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .fullName(fullName)
                .specialty(entity.getSpecialty())
                .rating(entity.getRating())
                .totalReviews(entity.getTotalReviews())
                .build();
    }
}
