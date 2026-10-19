package com.core.beautyshop.modules.inventory.application.service;
import com.core.beautyshop.modules.inventory.domain.*;
import com.core.beautyshop.shared.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.*;
import java.util.*;
@Service @RequiredArgsConstructor
public class BatchRecallService {
 private final BatchRecallRepository recalls;private final WarehouseStockRepository stocks;private final JdbcTemplate jdbc;
 private final RecallOutreachRepository outreach;
 private final com.fasterxml.jackson.databind.ObjectMapper mapper;
 public record Contact(Long subjectId,String subjectType,String status,String outcome){}
 public record Command(String reference,Long variantId,String batchCode,String reason){}
 @Transactional(readOnly=true)public Page<BatchRecall> list(Pageable p){return recalls.findAll(p);}
 @Transactional public BatchRecall recall(Command c){
  if(c==null||c.reference()==null||c.reference().isBlank()||c.reference().length()>100||c.variantId()==null||c.batchCode()==null||c.batchCode().isBlank()||c.batchCode().trim().length()>100||c.reason()==null||c.reason().isBlank()||c.reason().length()>500)throw new BusinessException("Recall reference, SKU, batch code and reason required");
  var batches=stocks.lockRecallBatches(c.variantId(),c.batchCode().trim());if(batches.isEmpty())throw new BusinessException("No stock batch matches this SKU and batch code");
  var prior=recalls.findByReference(c.reference());if(prior.isPresent()){var r=prior.get();if(!r.getVariantId().equals(c.variantId())||!r.getBatchCode().equals(c.batchCode().trim())||!r.getReason().equals(c.reason()))throw new BusinessException("Recall reference conflicts");return r;}
  var row=new BatchRecall();row.setReference(c.reference());row.setVariantId(c.variantId());row.setBatchCode(c.batchCode().trim());row.setReason(c.reason());
  for(var stock:batches){stock.setIsBlocked(true);stock.setBlockReason(c.reason());}return recalls.saveAndFlush(row);
 }
 @Transactional public void close(Long id,String reason){
  var row=recalls.lock(id).orElseThrow(()->new ResourceNotFoundException("Recall not found"));
  var batches=stocks.lockRecallBatches(row.getVariantId(),row.getBatchCode());
  if(reason==null||reason.isBlank()||reason.length()>500)throw new BusinessException("Recall closure reason required");
  if(trace(id).stream().anyMatch(r->!"RESOLVED".equals(r.get("outreachStatus"))))throw new BusinessException("Resolve each affected customer or appointment outcome before closing the recall");
  row.setActive(false);row.setClosureReason(reason);recalls.flush();
  if(recalls.findByVariantIdAndBatchCodeAndActiveTrue(row.getVariantId(),row.getBatchCode()).isEmpty())for(var stock:batches){stock.setIsBlocked(false);stock.setBlockReason(null);}
 }
 @Transactional(readOnly=true) public List<Map<String,Object>> trace(Long id){
  var row=recalls.findById(id).orElseThrow(()->new ResourceNotFoundException("Recall not found"));
  var result=new ArrayList<Map<String,Object>>(jdbc.queryForList("SELECT o.id orderId,o.order_number orderNumber,o.user_id userId,o.customer_name customerName,o.customer_phone customerPhone,COALESCE(o.customer_email,u.email) customerEmail,o.status,a.quantity,a.returned_quantity returnedQuantity,a.stock_id stockId FROM stock_allocations a JOIN warehouse_stocks s ON s.id=a.stock_id JOIN orders o ON o.order_number=a.order_number LEFT JOIN users u ON u.id=o.user_id WHERE s.product_variant_id=? AND s.batch_code=? AND a.status IN ('DEDUCTED','QUARANTINED') ORDER BY o.id DESC",row.getVariantId(),row.getBatchCode()));
  for(var item:result){item.put("subjectType","ORDER");item.put("subjectId",item.get("orderId"));}
  Set<Long> stockIds=new HashSet<>();for(var stock:stocks.findByProductVariantId(row.getVariantId()))if(Objects.equals(stock.getBatchCode(),row.getBatchCode()))stockIds.add(stock.getId());
  var usages=jdbc.queryForList("SELECT m.appointment_id appointmentId,m.appointment_item_id appointmentItemId,m.reference usageReference,m.allocation_json,a.user_id userId,u.full_name customerName,u.phone customerPhone,u.email customerEmail,a.status FROM spa_material_usages m JOIN appointments a ON a.id=m.appointment_id LEFT JOIN users u ON u.id=a.user_id WHERE m.variant_id=? ORDER BY m.appointment_id DESC",row.getVariantId());
  for(var usage:usages){
   try{
    var allocations=mapper.readValue(String.valueOf(usage.get("allocation_json")),new com.fasterxml.jackson.core.type.TypeReference<List<com.core.beautyshop.modules.inventory.api.dto.InventoryConsumptionResult.ConsumptionBatch>>(){});
    int affected=allocations.stream().filter(b->stockIds.contains(b.stockId())).mapToInt(com.core.beautyshop.modules.inventory.api.dto.InventoryConsumptionResult.ConsumptionBatch::quantity).sum();if(affected<=0)continue;
    usage.remove("allocation_json");usage.put("quantity",affected);usage.put("returnedQuantity",0);usage.put("subjectType","APPOINTMENT");usage.put("subjectId",usage.get("appointmentId"));result.add(usage);
   }catch(com.fasterxml.jackson.core.JsonProcessingException failure){throw new IllegalStateException("Cannot trace historical Spa batch allocation",failure);}
  }
  var contacts=outreach.findByRecallId(id);for(var affected:result){var contact=contacts.stream().filter(c->c.getSubjectType().equals(affected.get("subjectType"))&&c.getSubjectId().longValue()==((Number)affected.get("subjectId")).longValue()).findFirst().orElse(null);affected.put("outreachStatus",contact==null?"OPEN":contact.getStatus());affected.put("outreachOutcome",contact==null?null:contact.getOutcome());affected.put("outreachActorId",contact==null?null:contact.getActorId());}
  return result;
 }
 @Transactional public RecallOutreach contact(Long id,Contact c){
  recalls.lock(id).orElseThrow(()->new ResourceNotFoundException("Recall not found"));
  if(c==null||c.subjectId()==null||c.subjectType()==null||c.status()==null||!Set.of("ORDER","APPOINTMENT").contains(c.subjectType())||!Set.of("OPEN","CONTACTED","RESOLVED").contains(c.status())||c.outcome()==null||c.outcome().isBlank()||c.outcome().length()>1000)throw new BusinessException("Affected customer, contact status and factual outcome required");
  boolean affected=trace(id).stream().anyMatch(r->c.subjectType().equals(r.get("subjectType"))&&c.subjectId().longValue()==((Number)r.get("subjectId")).longValue());if(!affected)throw new BusinessException("This order or appointment did not receive the recalled batch");
  var row=outreach.findByRecallIdAndSubjectTypeAndSubjectId(id,c.subjectType(),c.subjectId()).orElseGet(RecallOutreach::new);row.setRecallId(id);row.setSubjectType(c.subjectType());row.setSubjectId(c.subjectId());row.setStatus(c.status());row.setOutcome(c.outcome());row.setActorId(com.core.beautyshop.shared.security.utils.SecurityUtils.getCurrentUserId());outreach.saveAndFlush(row);
  jdbc.update("INSERT INTO recall_outreach_events(recall_id,subject_type,subject_id,status,outcome,actor_id,created_at) VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP)",id,c.subjectType(),c.subjectId(),c.status(),c.outcome(),row.getActorId());return row;
 }
 @Transactional(readOnly=true)public List<Map<String,Object>> history(Long id){recalls.findById(id).orElseThrow(()->new ResourceNotFoundException("Recall not found"));return jdbc.queryForList("SELECT subject_type subjectType,subject_id subjectId,status,outcome,actor_id actorId,created_at createdAt FROM recall_outreach_events WHERE recall_id=? ORDER BY id DESC",id);}
}
