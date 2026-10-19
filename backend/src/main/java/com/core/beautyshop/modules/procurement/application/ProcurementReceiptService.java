package com.core.beautyshop.modules.procurement.application;
import com.core.beautyshop.modules.procurement.domain.*;
import com.core.beautyshop.modules.inventory.api.InventoryAdminFacade;
import com.core.beautyshop.shared.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.util.*;
@Service @RequiredArgsConstructor
public class ProcurementReceiptService {
 private final PurchaseOrderRepository orders;private final ProcurementMovementRepository movements;private final InventoryAdminFacade inventory;private final ObjectMapper mapper;
 public record Line(Long itemId,int quantity,String batchCode,LocalDate expirationDate){}
 public record Receipt(String reference,String reason,List<Line> lines){}
 public record Payment(String reference,String reason,BigDecimal amount){}
 public record Financials(Long orderId,BigDecimal receivedValue,BigDecimal paidAmount,BigDecimal dueAmount,List<ProcurementMovement> movements){}
 @Transactional(readOnly=true)public Financials financials(Long id){return view(orders.findById(id).orElseThrow(()->new ResourceNotFoundException("Purchase order not found")));}
 @Transactional public Financials receive(Long id,Receipt c){
  var order=lock(id);validate(c);if(replay(order,c.reference(),"RECEIPT",json(c.lines()),c.reason(),null))return view(order);
  if(order.getStatus()!=PurchaseOrder.Status.APPROVED&&order.getStatus()!=PurchaseOrder.Status.PARTIALLY_RECEIVED)throw new BusinessException("Purchase order must be approved or partially received");
  inventory.validateWarehouse(order.getWarehouseId());BigDecimal amount=BigDecimal.ZERO;Set<Long> seen=new HashSet<>();
  for(var line:c.lines()){
   var item=item(order,line,seen);
   if(line.quantity()>item.getQuantity()-item.getReceivedQuantity())throw new BusinessException("Receipt exceeds unreceived ordered quantity");
   if(line.batchCode()==null||line.batchCode().isBlank()||line.batchCode().length()>100||line.expirationDate()==null||!line.expirationDate().isAfter(com.core.beautyshop.shared.time.BusinessTime.today()))throw new BusinessException("Actual batch code and future expiration date required at goods receipt");
   inventory.receiveBatch(order.getWarehouseId(),item.getProductVariantId(),line.quantity(),item.getUnitCost(),line.batchCode().trim(),line.expirationDate(),c.reference());
   item.setReceivedQuantity(item.getReceivedQuantity()+line.quantity());amount=amount.add(item.getUnitCost().multiply(BigDecimal.valueOf(line.quantity())));
  }
  order.setStatus(order.getItems().stream().allMatch(i->i.getQuantity().equals(i.getReceivedQuantity()))?PurchaseOrder.Status.RECEIVED:PurchaseOrder.Status.PARTIALLY_RECEIVED);order.setReceivedAt(Instant.now());
  record(order,c.reference(),"RECEIPT",amount,c.reason(),json(c.lines()));return view(order);
 }
 @Transactional public Financials supplierReturn(Long id,Receipt c){
  var order=lock(id);validate(c);if(replay(order,c.reference(),"SUPPLIER_RETURN",json(c.lines()),c.reason(),null))return view(order);
  if(order.getStatus()!=PurchaseOrder.Status.RECEIVED&&order.getStatus()!=PurchaseOrder.Status.PARTIALLY_RECEIVED)throw new BusinessException("Receive goods before supplier return");
  Set<Long> seen=new HashSet<>();BigDecimal amount=BigDecimal.ZERO;var history=movements.findByPurchaseOrderIdOrderByIdDesc(id);
  for(var line:c.lines()){
   var item=item(order,line,seen);String code=line.batchCode()==null?"":line.batchCode().trim();
   int journaled=0;boolean receiptJournal=false;
   for(var movement:history)if(movement.getKind().equals("RECEIPT")||movement.getKind().equals("SUPPLIER_RETURN")){
    for(var prior:read(movement.getLinesJson()))if(prior.itemId().equals(line.itemId())){
     if(movement.getKind().equals("RECEIPT"))receiptJournal=true;
     if(Objects.equals(prior.batchCode().trim(),code))journaled+=(movement.getKind().equals("RECEIPT")?1:-1)*prior.quantity();
    }
   }
   if(!receiptJournal&&Objects.equals(item.getBatchCode()==null?"":item.getBatchCode().trim(),code))journaled=item.getReceivedQuantity()-item.getReturnedQuantity();
   if(line.quantity()>item.getReceivedQuantity()-item.getReturnedQuantity()||line.quantity()>journaled)throw new BusinessException("Supplier return exceeds received quantity in this batch");
   inventory.returnToSupplier(order.getWarehouseId(),item.getProductVariantId(),code,line.quantity(),c.reference());item.setReturnedQuantity(item.getReturnedQuantity()+line.quantity());amount=amount.add(item.getUnitCost().multiply(BigDecimal.valueOf(line.quantity())));
  }
  record(order,c.reference(),"SUPPLIER_RETURN",amount,c.reason(),json(c.lines()));return view(order);
 }
 @Transactional public Financials payment(Long id,Payment c,boolean supplierRefund){
  var order=lock(id);if(c==null||c.amount()==null||c.amount().signum()<=0||c.amount().stripTrailingZeros().scale()>0)throw new BusinessException("Positive whole-VND amount required");reference(c.reference(),c.reason());
  String kind=supplierRefund?"SUPPLIER_REFUND":"PAYMENT";if(replay(order,c.reference(),kind,"[]",c.reason(),c.amount()))return view(order);
  BigDecimal due=received(order).subtract(order.getPaidAmount());BigDecimal allowed=supplierRefund?due.negate():due;
  if(c.amount().compareTo(allowed)>0)throw new BusinessException("Amount exceeds supplier payable or recoverable credit");
  order.setPaidAmount(supplierRefund?order.getPaidAmount().subtract(c.amount()):order.getPaidAmount().add(c.amount()));record(order,c.reference(),kind,c.amount(),c.reason(),"[]");return view(order);
 }
 private PurchaseOrderItem item(PurchaseOrder order,Line line,Set<Long> seen){if(line==null||line.itemId()==null||line.quantity()<=0||!seen.add(line.itemId()))throw new BusinessException("Unique PO item and positive quantity required");return order.getItems().stream().filter(i->i.getId().equals(line.itemId())).findFirst().orElseThrow(()->new BusinessException("Item does not belong to purchase order"));}
 private void validate(Receipt c){if(c==null||c.lines()==null||c.lines().isEmpty()||c.lines().size()>100)throw new BusinessException("Goods receipt lines required");reference(c.reference(),c.reason());}
 private void reference(String reference,String reason){if(reference==null||reference.isBlank()||reference.length()>100||reason==null||reason.isBlank()||reason.length()>500)throw new BusinessException("Stable document reference and reason required");}
 private PurchaseOrder lock(Long id){return orders.findByIdForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Purchase order not found"));}
 private BigDecimal received(PurchaseOrder o){return o.getItems().stream().map(i->i.getUnitCost().multiply(BigDecimal.valueOf(i.getReceivedQuantity()-i.getReturnedQuantity()))).reduce(BigDecimal.ZERO,BigDecimal::add);}
 private Financials view(PurchaseOrder o){BigDecimal received=received(o);return new Financials(o.getId(),received,o.getPaidAmount(),received.subtract(o.getPaidAmount()),movements.findByPurchaseOrderIdOrderByIdDesc(o.getId()));}
 private boolean replay(PurchaseOrder o,String reference,String kind,String lines,String reason,BigDecimal amount){var prior=movements.findByReference(reference);if(prior.isEmpty())return false;var m=prior.get();if(!m.getPurchaseOrderId().equals(o.getId())||!m.getKind().equals(kind)||!m.getLinesJson().equals(lines)||!m.getReason().equals(reason)||amount!=null&&amount.compareTo(m.getAmount())!=0)throw new BusinessException("Document reference reused for different data");return true;}
 private void record(PurchaseOrder o,String reference,String kind,BigDecimal amount,String reason,String lines){var m=new ProcurementMovement();m.setPurchaseOrderId(o.getId());m.setReference(reference);m.setKind(kind);m.setAmount(amount);m.setReason(reason);m.setLinesJson(lines);movements.saveAndFlush(m);}
 private String json(Object o){try{return mapper.writeValueAsString(o);}catch(Exception e){throw new IllegalArgumentException(e);}}
 private List<Line> read(String json){try{return mapper.readValue(json,new TypeReference<List<Line>>(){});}catch(Exception e){throw new IllegalStateException(e);}}
}
