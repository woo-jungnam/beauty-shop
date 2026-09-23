package com.core.beautyshop.modules.catalog.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    Optional<Ingredient> findBySlugAndIsDeletedFalse(String slug);

    Optional<Ingredient> findByIdAndIsDeletedFalse(Long id);

    Page<Ingredient> findByIsDeletedFalse(Pageable pageable);

    boolean existsBySlug(String slug);
}
