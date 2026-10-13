package com.core.beautyshop.modules.spa.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import org.springframework.data.domain.*;

@Repository
public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Long> {
    Optional<ServiceCategory> findBySlug(String slug);
    Page<ServiceCategory> findByIsDeletedFalse(Pageable pageable);
}
