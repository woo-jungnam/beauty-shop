package com.core.beautyshop.modules.spa.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BeautyServiceRepository extends JpaRepository<BeautyService, Long> {

    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT s FROM BeautyService s WHERE s.isActive = true")
    List<BeautyService> findAllActiveWithCategory();

    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT s FROM BeautyService s WHERE s.id = :id")
    Optional<BeautyService> findWithCategoryById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT s FROM BeautyService s WHERE s.slug = :slug")
    Optional<BeautyService> findWithCategoryBySlug(@Param("slug") String slug);

    Optional<BeautyService> findBySlug(String slug);
}
