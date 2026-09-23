package com.core.beautyshop.modules.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductIngredientRepository extends JpaRepository<ProductIngredient, Long> {
    List<ProductIngredient> findByProductIdOrderByDisplayOrderAsc(Long productId);
}
