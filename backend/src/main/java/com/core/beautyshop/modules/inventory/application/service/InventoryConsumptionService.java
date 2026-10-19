package com.core.beautyshop.modules.inventory.application.service;
import com.core.beautyshop.modules.inventory.domain.*;
import com.core.beautyshop.modules.inventory.api.InventoryExpiryPolicy;
import com.core.beautyshop.shared.time.BusinessTime;
import com.core.beautyshop.modules.inventory.api.dto.InventoryConsumptionResult;
import com.core.beautyshop.modules.inventory.api.dto.InventoryConsumptionResult.ConsumptionBatch;
import com.core.beautyshop.modules.inventory.domain.enums.InventoryTransactionType;
import com.core.beautyshop.shared.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
@Service @RequiredArgsConstructor
public class InventoryConsumptionService {
    private final InventoryConsumptionRepository consumptions;
    private final WarehouseStockRepository stocks;
    private final InventoryBatchService batches;
    private final InventoryLedgerService ledger;
    private final ObjectMapper mapper;
    @Transactional
    public InventoryConsumptionResult consume(String reference,Long variantId,Long warehouseId,int quantity) {
        if(reference==null||reference.isBlank()||reference.length()>100||variantId==null||warehouseId==null||quantity<=0) throw new BusinessException("Reference, SKU, warehouse and positive quantity required");
        // Serialize replays even when their original warehouse has since been deactivated/deleted.
        var warehouse = batches.lockWarehouseForHistory(warehouseId);
        var existing=consumptions.findByReference(reference);
        if(existing.isPresent()) {
            var row=existing.get();
            if(!row.getVariantId().equals(variantId)||!row.getWarehouseId().equals(warehouseId)||row.getQuantity()!=quantity) throw new BusinessException("Consumption reference reused with different data");
            try { return mapper.readValue(row.getBatchSnapshot(),InventoryConsumptionResult.class); } catch(Exception failure) { throw new IllegalStateException("Invalid consumption snapshot",failure); }
        }
        if (Boolean.TRUE.equals(warehouse.getIsDeleted()) || !Boolean.TRUE.equals(warehouse.getIsActive()))
            throw new BusinessException("Kho hàng đã bị xóa hoặc ngưng hoạt động");
        var businessDate = BusinessTime.today();
        int remaining=quantity; BigDecimal cost=BigDecimal.ZERO; List<ConsumptionBatch> used=new ArrayList<>();
        for(var stock:stocks.lockAvailableBatches(variantId, InventoryExpiryPolicy.saleCutoff(businessDate))) {
            if(!warehouseId.equals(stock.getWarehouse().getId())) continue;
            StockAllocationService.requireSellable(stock, businessDate);
            int take=Math.min(remaining,stock.getQuantity()-stock.getReservedQuantity()); if(take<=0)continue;
            int before=stock.getQuantity();stock.setQuantity(before-take);
            ledger.record(stock,InventoryTransactionType.CONSUMPTION,-take,before,stock.getQuantity(),"SPA_USAGE",reference,"Actual service consumable usage");
            used.add(new ConsumptionBatch(stock.getId(),stock.getBatchCode(),stock.getExpirationDate(),take,stock.getCostPrice()));
            cost=cost.add(stock.getCostPrice().multiply(BigDecimal.valueOf(take)));remaining-=take;if(remaining==0)break;
        }
        if(remaining!=0)throw new BusinessException("Insufficient unreserved stock for service consumption");
        var result=new InventoryConsumptionResult(reference,variantId,warehouseId,quantity,cost,List.copyOf(used));
        var row=new InventoryConsumption();row.setReference(reference);row.setVariantId(variantId);row.setWarehouseId(warehouseId);row.setQuantity(quantity);row.setTotalCost(cost);
        try{row.setBatchSnapshot(mapper.writeValueAsString(result));}catch(Exception failure){throw new IllegalStateException(failure);}
        consumptions.saveAndFlush(row);return result;
    }
}
