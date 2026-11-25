package com.core.beautyshop.modules.procurement.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.*;

public interface ProcurementMovementRepository
        extends JpaRepository<ProcurementMovement, Long>, JpaSpecificationExecutor<ProcurementMovement> {
    Optional<ProcurementMovement> findByReference(String reference);

    List<ProcurementMovement> findByPurchaseOrderIdOrderByIdDesc(Long id);
}
