package com.core.beautyshop.modules.order.domain;

import java.math.BigDecimal;

import com.core.beautyshop.shared.domain.Base;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem extends Base {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_variant_id", nullable = false)
    private Long productVariantId;

    @Column(name = "sku", length = 100)
    private String sku;

    @Column(name = "product_name", length = 255)
    private String productName;

    @Column(name = "variant_name", length = 255)
    private String variantName;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "discount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;
}
