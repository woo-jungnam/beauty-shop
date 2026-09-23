package com.core.beautyshop.modules.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductSkinCompatibilityRepository extends JpaRepository<ProductSkinCompatibility, Long> {
    List<ProductSkinCompatibility> findByProductId(Long productId);
}
