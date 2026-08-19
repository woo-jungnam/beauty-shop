package com.core.beautyshop.shared.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BaseProductAttribute {

    @Column(name = "weight", precision = 10, scale = 2)
    private BigDecimal weight;

    @Column(name = "weight_unit", length = 10)
    private String weightUnit;

    @Column(name = "length", precision = 10, scale = 2)
    private BigDecimal length;

    @Column(name = "width", precision = 10, scale = 2)
    private BigDecimal width;

    @Column(name = "height", precision = 10, scale = 2)
    private BigDecimal height;

    @Column(name = "dimension_unit", length = 10)
    private String dimensionUnit;

    @Column(name = "expiry_months")
    private Integer expiryMonths;

    @Column(name = "expiry_after_opening_months")
    private Integer expiryAfterOpeningMonths;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "storage_condition", length = 255)
    private String storageCondition;

    @Column(name = "manufacturer_name", length = 255)
    private String manufacturerName;

    @Column(name = "manufacturing_country", length = 100)
    private String manufacturingCountry;
}
