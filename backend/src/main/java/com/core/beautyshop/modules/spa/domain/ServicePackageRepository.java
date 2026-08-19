package com.core.beautyshop.modules.spa.domain;

import com.core.beautyshop.modules.spa.domain.ServicePackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServicePackageRepository extends JpaRepository<ServicePackage, Long> {
}
