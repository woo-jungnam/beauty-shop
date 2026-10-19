package com.core.beautyshop.modules.inventory.application.service;
import com.core.beautyshop.modules.inventory.domain.*;
import com.core.beautyshop.modules.inventory.application.dto.request.WarehouseStockRequest;
import com.core.beautyshop.modules.inventory.application.dto.response.WarehouseStockResponse;
import com.core.beautyshop.shared.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class InventoryCommandService{
 private final InventoryCommandRepository commands;private final InventoryBatchService batches;private final WarehouseStockRepository stocks;private final WarehouseStockService stockService;private final InventoryMovementService movements;private final ObjectMapper mapper;
 @Transactional public WarehouseStockResponse receive(Long warehouseId,WarehouseStockRequest request,String key){
  String hash=hash("RECEIVE:"+warehouseId+":"+json(request));batches.lockWarehouse(warehouseId);String commandKey=key(key);var prior=commands.findByCommandKey(commandKey);
  if(prior.isPresent()){check(prior.get(),hash);try{return mapper.readValue(prior.get().getResultJson(),WarehouseStockResponse.class);}catch(Exception e){throw new IllegalStateException(e);}}
  var result=stockService.receiveStock(warehouseId,request,"MANUAL_RECEIPT",key);record(commandKey,hash,json(result));return result;
 }
 @Transactional public void transfer(Long stockId,Long targetId,int quantity,String reason,String key){
  String hash=hash("TRANSFER:"+stockId+":"+targetId+":"+quantity+":"+reason);Long sourceId=stocks.findWarehouseIdByStockId(stockId).orElseThrow(()->new BusinessException("Source batch not found"));if(targetId==null)throw new BusinessException("Target warehouse required");
  batches.lockWarehouse(Math.min(sourceId,targetId));batches.lockWarehouse(Math.max(sourceId,targetId));String commandKey=key(key);var prior=commands.findByCommandKey(commandKey);if(prior.isPresent()){check(prior.get(),hash);return;}
  movements.transfer(stockId,targetId,quantity,reason);record(commandKey,hash,"{}");
 }
 private String key(String key){if(key==null||key.isBlank()||key.length()>100)throw new BusinessException("Idempotency-Key (1-100 chars) required");return key;}
 private void check(InventoryCommand row,String hash){if(!row.getCommandHash().equals(hash))throw new BusinessException("Idempotency-Key reused for a different inventory command");}
 private void record(String key,String hash,String result){var row=new InventoryCommand();row.setCommandKey(key);row.setCommandHash(hash);row.setResultJson(result);commands.saveAndFlush(row);}
 private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException(e);}}
 private String hash(String value){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
