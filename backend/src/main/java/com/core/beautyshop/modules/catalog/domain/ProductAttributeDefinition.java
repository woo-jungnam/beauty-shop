package com.core.beautyshop.modules.catalog.domain;

import java.util.List;

import com.core.beautyshop.shared.domain.Base;
import com.core.beautyshop.modules.catalog.domain.enums.AttributeDataType;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product_attribute_definitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductAttributeDefinition extends Base {

    @Column(name = "attribute_name", nullable = false, length = 150)
    private String attributeName;

    @Column(name = "attribute_code", nullable = false, unique = true, length = 100)
    private String attributeCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 20)
    @Builder.Default
    private AttributeDataType dataType = AttributeDataType.STRING;

    @Column(name = "unit", length = 30)
    private String unit;

    @Column(name = "default_value", length = 255)
    private String defaultValue;
    @Builder.Default
    @Column(name = "is_required", nullable = false)
    private Boolean isRequired = false;
    @Builder.Default
    @Column(name = "is_filterable", nullable = false)
    private Boolean isFilterable = false;
    @Builder.Default
    @Column(name = "is_visible", nullable = false)
    private Boolean isVisible = true;
    @Builder.Default
    @Column(name = "display_order")
    private Integer displayOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @OneToMany(mappedBy = "attributeDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductAttributeValue> attributeValues;
}
