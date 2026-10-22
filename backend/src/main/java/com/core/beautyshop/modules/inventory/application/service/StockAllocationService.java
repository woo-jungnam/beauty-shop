package com.core.beautyshop.modules.inventory.application.service;

import com.core.beautyshop.modules.inventory.domain.*;
import com.core.beautyshop.modules.inventory.api.InventoryExpiryPolicy;
import com.core.beautyshop.shared.time.BusinessTime;
import com.core.beautyshop.modules.inventory.domain.enums.InventoryTransactionType;
import com.core.beautyshop.modules.inventory.api.exception.InsufficientStockException;
import com.core.beautyshop.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.List;

@Service @RequiredArgsConstructor
public class StockAllocationService {
    private final WarehouseStockRepository stocks;
    private final StockAllocationRepository allocations;
    private final InventoryLedgerService ledger;

    @Transactional
    public void reserve(String orderNumber, Long variantId, int quantity) {
        reserve(orderNumber, variantId, quantity, BusinessTime.today());
    }

    private void reserve(String orderNumber, Long variantId, int quantity, LocalDate businessDate) {
        validate(orderNumber, variantId, quantity);
        List<StockAllocation> existing = allocations.lockAllocations(orderNumber, variantId);
        if (!existing.isEmpty()) {
            if (existing.stream().mapToInt(StockAllocation::getQuantity).sum() == quantity
                    && existing.stream().allMatch(a -> a.getStatus() == StockAllocation.Status.RESERVED)) return;
            throw new BusinessException("Stock already allocated with a different quantity or state");
        }
        int remaining = quantity;
        for (WarehouseStock stock : stocks.lockAvailableBatches(variantId, InventoryExpiryPolicy.saleCutoff(businessDate))) {
            requireSellable(stock, businessDate);
            int take = Math.min(remaining, Math.max(0, stock.getQuantity() - stock.getReservedQuantity()));
            if (take == 0) continue;
            stock.setReservedQuantity(stock.getReservedQuantity() + take);
            StockAllocation row = new StockAllocation(); row.setOrderNumber(orderNumber); row.setVariantId(variantId);
            row.setStockId(stock.getId()); row.setQuantity(take); allocations.save(row);
            remaining -= take;
            if (remaining == 0) break;
        }
        if (remaining != 0) throw new InsufficientStockException("Insufficient sellable stock for variant #" + variantId);
    }

    @Transactional
    public void validateForDispatch(String orderNumber, Long variantId, int quantity) {
        LocalDate businessDate = BusinessTime.today();
        for (StockAllocation row : owned(orderNumber, variantId, quantity)) {
            if (row.getStatus() != StockAllocation.Status.RESERVED && row.getStatus() != StockAllocation.Status.DEDUCTED)
                throw new BusinessException("Order stock is no longer available for dispatch");
            if (row.getReturnedQuantity() != 0) throw new BusinessException("Returned stock cannot be dispatched");
            requireSellable(stock(row), businessDate);
        }
    }

    @Transactional
    public void validateDeliveredAllocation(String orderNumber, Long variantId, int quantity) {
        if (owned(orderNumber, variantId, quantity).stream().anyMatch(row -> row.getStatus() != StockAllocation.Status.DEDUCTED))
            throw new BusinessException("Shipment stock deduction missing; reconcile stock before confirming delivery");
    }

    @Transactional
    public void transition(String orderNumber, Long variantId, int quantity, StockAllocation.Status target) {
        transition(orderNumber, variantId, quantity, target, BusinessTime.today());
    }

    private void transition(String orderNumber, Long variantId, int quantity, StockAllocation.Status target, LocalDate businessDate) {
        for (StockAllocation row : owned(orderNumber, variantId, quantity)) {
            if (row.getStatus() == target) continue;
            WarehouseStock stock = stock(row);
            int amount = row.getQuantity();
            if (target == StockAllocation.Status.RELEASED && row.getStatus() == StockAllocation.Status.DEDUCTED) {
                int before = stock.getQuantity(); stock.setQuantity(Math.addExact(before, amount));
                ledger.record(stock, InventoryTransactionType.CANCELLATION, amount, before, stock.getQuantity(), "ORDER", orderNumber, "Paid order cancelled before dispatch");
            } else if (target == StockAllocation.Status.QUARANTINED) {
                if (row.getStatus() != StockAllocation.Status.DEDUCTED && row.getStatus() != StockAllocation.Status.RESERVED)
                    throw new BusinessException("Stock cannot be returned from current state");
                if (row.getStatus() == StockAllocation.Status.RESERVED) {
                    checkReserved(stock, amount); stock.setQuantity(stock.getQuantity() - amount);
                    stock.setReservedQuantity(stock.getReservedQuantity() - amount);
                }
                int outstanding = amount - row.getReturnedQuantity();
                stock.setQuarantinedQuantity(Math.addExact(stock.getQuarantinedQuantity(), outstanding)); row.setReturnedQuantity(amount);
                ledger.record(stock, InventoryTransactionType.RETURN, outstanding, stock.getQuantity(), stock.getQuantity(), "ORDER", orderNumber, "Return awaiting inspection");
            } else {
                if (row.getStatus() != StockAllocation.Status.RESERVED) throw new BusinessException("Invalid stock allocation transition");
                checkReserved(stock, amount); int before = stock.getQuantity();
                if (target == StockAllocation.Status.DEDUCTED) {
                    requireSellable(stock, businessDate); stock.setQuantity(before - amount); row.setUnitCostSnapshot(stock.getCostPrice());
                    ledger.record(stock, InventoryTransactionType.SALE, -amount, before, stock.getQuantity(), "ORDER", orderNumber, "Order stock deducted");
                } else if (target == StockAllocation.Status.RELEASED) {
                    ledger.record(stock, InventoryTransactionType.CANCELLATION, amount, before, before, "ORDER", orderNumber, "Reservation released");
                } else throw new BusinessException("Unsupported stock transition");
                stock.setReservedQuantity(stock.getReservedQuantity() - amount);
            }
            row.setStatus(target);
        }
    }

    @Transactional
    public void returnPartial(String orderNumber, Long variantId, int quantity) {
        validate(orderNumber, variantId, quantity); var rows = allocations.lockAllocations(orderNumber, variantId);
        int available = rows.stream().filter(r -> r.getStatus() == StockAllocation.Status.DEDUCTED).mapToInt(r -> r.getQuantity() - r.getReturnedQuantity()).sum();
        if (available < quantity) throw new BusinessException("Return quantity exceeds fulfilled stock");
        int remaining = quantity;
        for (var row : rows) {
            if (row.getStatus() != StockAllocation.Status.DEDUCTED) continue;
            int take = Math.min(remaining, row.getQuantity() - row.getReturnedQuantity()); if (take == 0) continue;
            var stock = stock(row); stock.setQuarantinedQuantity(Math.addExact(stock.getQuarantinedQuantity(), take));
            row.setReturnedQuantity(row.getReturnedQuantity() + take);
            ledger.record(stock, InventoryTransactionType.RETURN, take, stock.getQuantity(), stock.getQuantity(), "ORDER", orderNumber, "Partial return to quarantine");
            remaining -= take; if (remaining == 0) break;
        }
    }
    private List<StockAllocation> owned(String orderNumber, Long variantId, int quantity) {
        validate(orderNumber, variantId, quantity); var rows = allocations.lockAllocations(orderNumber, variantId);
        if (rows.isEmpty() || rows.stream().mapToLong(StockAllocation::getQuantity).sum() != quantity
                || rows.stream().anyMatch(row -> !orderNumber.equals(row.getOrderNumber()) || !variantId.equals(row.getVariantId()) || row.getQuantity() <= 0))
            throw new BusinessException("Order batch allocation missing or inconsistent; reconcile stock first");
        return rows;
    }
    @Transactional
    public void replace(String orderNumber,Long variantId,int quantity) {
        LocalDate businessDate = BusinessTime.today();
        var rows=owned(orderNumber,variantId,quantity);
        boolean deducted=rows.stream().allMatch(r->r.getStatus()==StockAllocation.Status.DEDUCTED);
        if(!deducted&&!rows.stream().allMatch(r->r.getStatus()==StockAllocation.Status.RESERVED))throw new BusinessException("Only outstanding unreturned allocations can be replaced");
        if(rows.stream().anyMatch(r->r.getReturnedQuantity()>0))throw new BusinessException("Returned allocations cannot be replaced");
        transition(orderNumber,variantId,quantity,StockAllocation.Status.RELEASED,businessDate);
        allocations.deleteAll(rows);allocations.flush();reserve(orderNumber,variantId,quantity,businessDate);
        if(deducted)transition(orderNumber,variantId,quantity,StockAllocation.Status.DEDUCTED,businessDate);
    }
    private WarehouseStock stock(StockAllocation row) { return stocks.findByIdForUpdate(row.getStockId()).orElseThrow(() -> new BusinessException("Allocated batch not found")); }
    private void checkReserved(WarehouseStock stock, int quantity) {
        if (stock.getQuantity() < quantity || stock.getReservedQuantity() < quantity) throw new BusinessException("Allocated stock balances are inconsistent");
    }
    static void requireSellable(WarehouseStock stock, LocalDate businessDate) {
        if (Boolean.TRUE.equals(stock.getIsDeleted()) || Boolean.TRUE.equals(stock.getIsBlocked())
                || stock.getWarehouse() == null || !Boolean.TRUE.equals(stock.getWarehouse().getIsActive())
                || Boolean.TRUE.equals(stock.getWarehouse().getIsDeleted())
                || !InventoryExpiryPolicy.isSellable(stock.getExpirationDate(), businessDate))
            throw new BusinessException("Batch has 30 or fewer days remaining, is recalled or unavailable; replace allocation before dispatch");
    }
    private void validate(String orderNumber, Long variantId, int quantity) {
        if (orderNumber == null || orderNumber.isBlank() || variantId == null || variantId <= 0 || quantity <= 0)
            throw new BusinessException("Order reference, SKU and positive quantity are required");
    }
}
