package com.core.beautyshop.modules.procurement.domain;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long>, JpaSpecificationExecutor<PurchaseOrder> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from PurchaseOrder p left join fetch p.items where p.id=:id") Optional<PurchaseOrder> findByIdForUpdate(Long id);
}
