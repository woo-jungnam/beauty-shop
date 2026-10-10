package com.core.beautyshop.modules.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BannerRepository extends JpaRepository<Banner, Long> {

    List<Banner> findByIsDeletedFalseAndIsActiveTrueOrderBySortOrderAscIdAsc();

    List<Banner> findByPositionAndIsDeletedFalseAndIsActiveTrueOrderBySortOrderAscIdAsc(String position);

    List<Banner> findByIsDeletedFalseOrderByPositionAscSortOrderAscIdAsc();

    Optional<Banner> findByIdAndIsDeletedFalse(Long id);
}
