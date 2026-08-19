package com.core.beautyshop.modules.spa.domain;

import com.core.beautyshop.modules.spa.domain.ServiceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Long> {
    Optional<ServiceCategory> findBySlug(String slug);
}
