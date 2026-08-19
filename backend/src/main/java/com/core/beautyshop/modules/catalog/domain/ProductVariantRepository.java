package com.core.beautyshop.modules.catalog.domain;

import com.core.beautyshop.modules.catalog.domain.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    Optional<ProductVariant> findBySku(String sku);
    boolean existsBySku(String sku);
    List<ProductVariant> findByProductIdAndIsDeletedFalse(Long productId);
    Optional<ProductVariant> findByIdAndIsDeletedFalse(Long id);
}
