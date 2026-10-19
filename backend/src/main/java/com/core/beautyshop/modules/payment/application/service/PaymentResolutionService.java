package com.core.beautyshop.modules.payment.application.service;
import com.core.beautyshop.modules.payment.domain.*;
import com.core.beautyshop.modules.payment.domain.enums.TransactionStatus;
import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.shared.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
@Service @RequiredArgsConstructor
public class PaymentResolutionService {
 private final PaymentTransactionRepository transactions;private final PaymentResolutionRepository resolutions;private final OrderFacade orders;
 public record Command(String kind,String orderNumber,String reference,String reason){}
 @Transactional(readOnly=true)public Page<PaymentResolution> list(Pageable p){return resolutions.findAll(p);}
 @Transactional(readOnly=true)public Page<PaymentTransaction> exceptions(TransactionStatus status,Pageable p){
  if(status!=null&&status!=TransactionStatus.IGNORED&&status!=TransactionStatus.ORDER_NOT_FOUND)throw new BusinessException("Exception status must be IGNORED or ORDER_NOT_FOUND");
  return transactions.findAll((root,query,cb)->{
   var resolved=query.subquery(Long.class);var r=resolved.from(PaymentResolution.class);resolved.select(r.get("transactionId")).where(cb.equal(r.get("transactionId"),root.get("id")));
   return cb.and(cb.equal(cb.lower(root.get("transferType")),"in"),cb.greaterThan(root.get("amount"),java.math.BigDecimal.ZERO),status==null?root.get("status").in(TransactionStatus.IGNORED,TransactionStatus.ORDER_NOT_FOUND):cb.equal(root.get("status"),status),cb.not(cb.exists(resolved)));
  },p);
 }
 @Transactional public PaymentResolution resolve(Long id,Command c){
  if(c==null||!("MATCH".equals(c.kind())||"REFUND".equals(c.kind()))||c.reason()==null||c.reason().isBlank()||c.reason().length()>500)throw new BusinessException("Resolution kind MATCH/REFUND and evidence reason required");
  var tx=transactions.findByIdForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Payment transaction not found"));
  var existing=resolutions.findByTransactionId(id);if(existing.isPresent()){var r=existing.get();if(!r.getKind().equals(c.kind())||!java.util.Objects.equals(r.getOrderNumber(),c.orderNumber())||!java.util.Objects.equals(r.getReference(),c.reference())||!r.getReason().equals(c.reason()))throw new BusinessException("Receipt already resolved differently");return r;}
  if(!"in".equalsIgnoreCase(tx.getTransferType())||tx.getAmount().signum()<=0||!(tx.getStatus()==TransactionStatus.IGNORED||tx.getStatus()==TransactionStatus.ORDER_NOT_FOUND))throw new BusinessException("Only unassigned valid incoming receipts can be resolved");
  if("MATCH".equals(c.kind())){
   if(c.orderNumber()==null||orders.findPaymentState(c.orderNumber()).isEmpty())throw new BusinessException("Target order number not found");
   boolean paid=orders.markOrderAsPaid(c.orderNumber(),tx.getAmount(),tx.getReferenceCode());tx.setOrderNumber(c.orderNumber());
   String state=orders.findPaymentState(c.orderNumber()).orElseThrow();
   tx.setStatus(state.equals("CANCELLED")||state.equals("RETURNED")||state.equals("RETURN_TO_SENDER")||state.equals("REFUNDED_VISIT")?TransactionStatus.CANCELLED_ORDER_RECEIVED:paid?TransactionStatus.SUCCESS:TransactionStatus.PARTIALLY_PAID);
  }else if(c.reference()==null||c.reference().isBlank()||c.reference().length()>100)throw new BusinessException("Actual refund bank reference required");
  var row=new PaymentResolution();row.setTransactionId(id);row.setKind(c.kind());row.setOrderNumber(c.orderNumber());row.setReference(c.reference());row.setReason(c.reason());row.setAmount(tx.getAmount());return resolutions.saveAndFlush(row);
 }
}
