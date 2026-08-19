package com.core.beautyshop.modules.spa.application.dto.response;

import com.core.beautyshop.modules.spa.domain.Staff;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffResponse {
    private Long id;
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
