package com.core.beautyshop.modules.catalog.domain;

import com.core.beautyshop.shared.domain.Base;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product_tags")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductTag extends Base {

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 100)
    private String slug;
}
