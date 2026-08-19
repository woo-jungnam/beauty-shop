package com.core.beautyshop.modules.catalog.domain;

import com.core.beautyshop.modules.catalog.domain.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderByDisplayOrderAsc(Long productId);

    void deleteByProductId(Long productId);
}
