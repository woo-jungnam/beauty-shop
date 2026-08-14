package com.core.beautyshop.repository;

import com.core.beautyshop.entities.service.BeautyService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BeautyServiceRepository extends JpaRepository<BeautyService, Long> {
    Optional<BeautyService> findBySlug(String slug);
}
