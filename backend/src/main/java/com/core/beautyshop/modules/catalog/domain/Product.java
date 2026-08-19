package com.core.beautyshop.modules.catalog.domain;

import java.math.BigDecimal;
import java.util.List;

import com.core.beautyshop.shared.domain.Base;
import com.core.beautyshop.modules.catalog.domain.enums.ProductStatus;
import com.core.beautyshop.modules.catalog.domain.enums.ProductType;
import com.core.beautyshop.modules.catalog.domain.enums.SkinType;
import com.core.beautyshop.modules.catalog.domain.enums.TargetGender;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends Base {

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 255)
    private String slug;

    @Column(name = "short_description", length = 500)
    private String shortDescription;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "base_price", precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ProductStatus status = ProductStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20)
    @Builder.Default
    private ProductType productType = ProductType.PRODUCT;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_gender", length = 10)
    private TargetGender targetGender;

    @Enumerated(EnumType.STRING)
    @Column(name = "skin_type", length = 20)
    private SkinType skinType;

    @Column(name = "ingredients", columnDefinition = "TEXT")
    private String ingredients;

    @Column(name = "how_to_use", columnDefinition = "TEXT")
    private String howToUse;

    @Column(name = "origin_country", length = 100)
    private String originCountry;

    @Column(name = "volume", length = 50)
    private String volume;

    @Column(name = "is_featured", nullable = false)
    @Builder.Default
    private Boolean isFeatured = false;

    @Column(name = "average_rating")
    @Builder.Default
    private Double averageRating = 0.0;

    @Column(name = "total_reviews")
    @Builder.Default
    private Integer totalReviews = 0;

    @Column(name = "total_sold")
    @Builder.Default
    private Long totalSold = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "product_categories",
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private List<Category> categories;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVariant> variants;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images;

    @ManyToMany
    @JoinTable(
        name = "product_tag_mapping",
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private List<ProductTag> tags;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductAttributeValue> attributeValues;
}
