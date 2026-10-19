package com.core.beautyshop.modules.procurement.domain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ProcurementMovementRepository extends JpaRepository<ProcurementMovement,Long>{Optional<ProcurementMovement> findByReference(String reference);List<ProcurementMovement> findByPurchaseOrderIdOrderByIdDesc(Long id);}
