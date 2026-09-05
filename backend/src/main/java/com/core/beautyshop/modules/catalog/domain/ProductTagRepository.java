package com.core.beautyshop.modules.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductTagRepository extends JpaRepository<ProductTag, Long> {

    Optional<ProductTag> findBySlug(String slug);

    List<ProductTag> findByNameContainingIgnoreCase(String name);

    boolean existsBySlug(String slug);
}
