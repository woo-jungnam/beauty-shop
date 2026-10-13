package com.core.beautyshop.modules.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SkinConcernRepository extends JpaRepository<SkinConcern, Long> {
    Optional<SkinConcern> findByCode(String code);
}
