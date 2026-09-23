package com.core.beautyshop.modules.inventory.application.service;
import com.core.beautyshop.modules.inventory.domain.WarehouseStockRepository;
import com.core.beautyshop.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class StockInspectionService {
    private final WarehouseStockRepository stocks;
    @com.core.beautyshop.shared.audit.api.annotation.AuditAction(action = "INSPECT_RETURN", resourceType = "WAREHOUSE_STOCK")
    @Transactional
    public void inspect(Long id, int quantity, boolean restock) {
        var stock = stocks.findByIdForUpdate(id).orElseThrow(() -> new BusinessException("Batch not found"));
        if (quantity <= 0 || quantity > stock.getQuarantinedQuantity()) throw new BusinessException("Invalid inspection quantity");
        if (restock && (Boolean.TRUE.equals(stock.getIsDeleted()) || stock.getExpirationDate() != null
                && !stock.getExpirationDate().isAfter(java.time.LocalDate.now()))) throw new BusinessException("Expired/deleted stock cannot be resold");
        stock.setQuarantinedQuantity(stock.getQuarantinedQuantity() - quantity);
        if (restock) stock.setQuantity(Math.addExact(stock.getQuantity(), quantity));
    }
}
