package com.core.beautyshop.dto.response.spa;

import com.core.beautyshop.entities.service.Staff;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StaffResponse {
    private Long id;
    private Long userId;
    private String fullName;
    private String specialty;
    private Double rating;
    private Integer totalReviews;

    public static StaffResponse fromEntity(Staff entity) {
        if (entity == null) return null;
        return StaffResponse.builder()
                .id(entity.getId())
                .userId(entity.getUser() != null ? entity.getUser().getId() : null)
                .fullName(entity.getUser() != null ? entity.getUser().getFullName() : null)
                .specialty(entity.getSpecialty())
                .rating(entity.getRating())
                .totalReviews(entity.getTotalReviews())
                .build();
    }
}
