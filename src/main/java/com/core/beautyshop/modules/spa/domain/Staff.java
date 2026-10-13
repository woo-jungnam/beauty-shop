package com.core.beautyshop.modules.spa.domain;

import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "staffs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Staff extends Base {

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "specialty", length = 250)
    private String specialty;

    @Column(name = "bio", length = 500)
    private String bio;

    @Column(name = "rating")
    @Builder.Default
    private Double rating = 0.0;

    @Column(name = "total_reviews")
    @Builder.Default
    private Integer totalReviews = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @OneToMany(mappedBy = "staff", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StaffServiceSkill> skills;
}
