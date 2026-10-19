package com.core.beautyshop.modules.inventory.application.service;
import com.core.beautyshop.modules.inventory.domain.WarehouseStockRepository;
import com.core.beautyshop.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class StockInspectionService {
    private final WarehouseStockRepository stocks;
    private final InventoryLedgerService ledger;
    @com.core.beautyshop.shared.audit.api.annotation.AuditAction(action = "INSPECT_RETURN", resourceType = "WAREHOUSE_STOCK")
    @Transactional
    public void inspect(Long id, int quantity, boolean restock) {
        var stock = stocks.findByIdForUpdate(id).orElseThrow(() -> new BusinessException("Không tìm thấy lô hàng"));
        if (quantity <= 0 || quantity > stock.getQuarantinedQuantity()) throw new BusinessException("Số lượng hàng kiểm định không hợp lệ");
        if (restock) StockAllocationService.requireSellable(stock, com.core.beautyshop.shared.time.BusinessTime.today());
        stock.setQuarantinedQuantity(stock.getQuarantinedQuantity() - quantity);
        if (restock) {
            int before = stock.getQuantity();
            stock.setQuantity(Math.addExact(before, quantity));
            ledger.record(stock, com.core.beautyshop.modules.inventory.domain.enums.InventoryTransactionType.ADJUSTMENT,
                    quantity, before, stock.getQuantity(), "RETURN_INSPECTION", String.valueOf(stock.getId()), "Returned goods approved for resale");
        } else {
            ledger.record(stock, com.core.beautyshop.modules.inventory.domain.enums.InventoryTransactionType.DISPOSAL,
                    -quantity, stock.getQuantity(), stock.getQuantity(), "RETURN_INSPECTION", String.valueOf(stock.getId()), "Returned goods disposed");
        }
    }
}
