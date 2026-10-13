package com.core.beautyshop.modules.spa.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ServiceFacilityRequirementRepository extends JpaRepository<ServiceFacilityRequirement, Long> {
    List<ServiceFacilityRequirement> findByServiceIdAndIsDeletedFalseOrderByResourceTypeAsc(Long serviceId);
}
