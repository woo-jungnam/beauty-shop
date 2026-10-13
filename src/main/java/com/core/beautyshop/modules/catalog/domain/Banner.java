package com.core.beautyshop.modules.catalog.domain;

import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "banners")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Banner extends Base {

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "badge", length = 100)
    private String badge;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "image_url", nullable = false, length = 1000)
    private String imageUrl;

    @Column(name = "target_url", length = 500)
    private String targetUrl;

    @Column(name = "cta_text", length = 50)
    private String ctaText;

    @Column(name = "position", nullable = false, length = 50)
    @Builder.Default
    private String position = "HERO_SLIDE";

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
