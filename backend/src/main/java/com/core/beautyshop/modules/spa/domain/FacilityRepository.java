package com.core.beautyshop.modules.spa.domain;

import com.core.beautyshop.modules.spa.domain.Facility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FacilityRepository extends JpaRepository<Facility, Long> {
}
