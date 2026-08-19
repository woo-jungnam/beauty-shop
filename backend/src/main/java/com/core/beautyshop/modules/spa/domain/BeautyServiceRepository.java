package com.core.beautyshop.modules.spa.domain;

import com.core.beautyshop.modules.spa.domain.BeautyService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BeautyServiceRepository extends JpaRepository<BeautyService, Long> {
    Optional<BeautyService> findBySlug(String slug);
}
