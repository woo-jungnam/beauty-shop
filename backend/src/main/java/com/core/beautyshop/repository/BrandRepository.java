package com.core.beautyshop.repository;

import com.core.beautyshop.entities.product.Brand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BrandRepository extends JpaRepository<Brand, Long> {

    Optional<Brand> findBySlugAndIsDeletedFalse(String slug);

    Optional<Brand> findByIdAndIsDeletedFalse(Long id);

    Page<Brand> findByIsDeletedFalse(Pageable pageable);

    boolean existsBySlug(String slug);
}
