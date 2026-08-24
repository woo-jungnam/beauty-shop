package com.core.beautyshop.modules.spa.domain;

import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "beauty_services")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BeautyService extends Base {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 150)
    private String slug;

    @Column(name = "short_description", length = 250)
    private String shortDescription;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "preparation_time_minutes")
    @Builder.Default
    private Integer preparationTimeMinutes = 15;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ServiceCategory category;

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StaffServiceSkill> staffSkills;
}
