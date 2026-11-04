package com.core.beautyshop.modules.spa.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BeautyServiceRepository extends JpaRepository<BeautyService, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from BeautyService s where s.id = :id and s.isDeleted = false")
    Optional<BeautyService> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from BeautyService s where s.id = :id and s.isDeleted = false and s.isActive = true")
    Optional<BeautyService> findAvailableByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT s FROM BeautyService s WHERE s.isActive = true AND s.isDeleted = false")
    List<BeautyService> findAllActiveWithCategory();

    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT s FROM BeautyService s WHERE s.id = :id AND s.isActive = true AND s.isDeleted = false")
    Optional<BeautyService> findWithCategoryById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT s FROM BeautyService s WHERE s.slug = :slug AND s.isActive = true AND s.isDeleted = false")
    Optional<BeautyService> findWithCategoryBySlug(@Param("slug") String slug);

    Optional<BeautyService> findBySlug(String slug);

    @EntityGraph(attributePaths = {"category"})
    Page<BeautyService> findByIsDeletedFalse(Pageable pageable);
}
