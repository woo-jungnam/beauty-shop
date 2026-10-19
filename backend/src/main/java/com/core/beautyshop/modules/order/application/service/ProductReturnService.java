package com.core.beautyshop.modules.order.application.service;
import com.core.beautyshop.modules.order.domain.*;
import com.core.beautyshop.modules.order.domain.enums.*;
import com.core.beautyshop.modules.order.api.event.OrderEvents;
import com.core.beautyshop.modules.inventory.api.InventoryFacade;
import com.core.beautyshop.shared.exception.*;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import java.math.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class ProductReturnService {
    private final OrderRepository orders;
    private final ProductReturnRepository returns;
    private final RefundConfirmationRepository refunds;
    private final InventoryFacade inventory;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher events;
    @org.springframework.beans.factory.annotation.Value("${app.order.return-window-days:14}")
    private int returnWindowDays=14;
    public record Line(Long orderItemId,int quantity) {}
    public record Request(String reason,List<Line> lines,List<String> evidenceUrls) {}
    public record View(Long id,Long orderId,String status,String reason,String decisionReason,BigDecimal refundAmount,List<Line> lines,List<String> evidenceUrls,Instant createdAt,Long replacementOrderId,String replacementReason,Instant replacementLinkedAt) {}
    public record Replacement(Long replacementOrderId,String reason){}
    @Transactional public View linkReplacement(Long id,Replacement c){
        var original=orderForReturn(id);var row=lockedReturn(id);
        if(c==null||c.replacementOrderId()==null||c.reason()==null||c.reason().isBlank()||c.reason().length()>500)throw new BusinessException("Replacement order and reason required");
        if(!"REFUNDED".equals(row.getStatus()))throw new BusinessException("Confirm the real return refund before linking a separate replacement sale");
        if(row.getReplacementOrderId()!=null){if(!row.getReplacementOrderId().equals(c.replacementOrderId())||!row.getReplacementReason().equals(c.reason()))throw new BusinessException("Replacement sale already linked differently");return view(row);}
        var replacement=orders.findById(c.replacementOrderId()).filter(o->!Boolean.TRUE.equals(o.getIsDeleted())).orElseThrow(()->new BusinessException("Replacement order not found"));
        boolean sameOwner=original.getUserId()!=null?original.getUserId().equals(replacement.getUserId()):replacement.getUserId()==null&&original.getGuestSessionId()!=null&&original.getGuestSessionId().equals(replacement.getGuestSessionId());
        if(!sameOwner||original.getId().equals(replacement.getId())||replacement.getAppointmentId()!=null||replacement.getServicePackageId()!=null||replacement.getStatus()==OrderStatus.CANCELLED||replacement.getItems()==null||replacement.getItems().isEmpty())throw new BusinessException("Replacement must be a separate active product sale belonging to the same customer or guest session");
        row.setReplacementOrderId(replacement.getId());row.setReplacementReason(c.reason());row.setReplacementLinkedAt(Instant.now());row.setReplacementLinkedBy(SecurityUtils.getCurrentUserId());return view(row);
    }
    public record Decision(boolean approved,String reason) {}
    public record Confirmation(String reference,String reason) {}
    public record Balances(BigDecimal paidAmount,BigDecimal refundedAmount,BigDecimal remainingOverpayment,BigDecimal returnLiability,BigDecimal unsettledCod){}
    @Transactional(readOnly=true) public Balances balances(Long id) {
        var order=orders.findById(id).orElseThrow(()->new ResourceNotFoundException("Order not found"));
        BigDecimal remaining=order.getPaidAmount().subtract(order.getRefundedAmount()).max(BigDecimal.ZERO);
        BigDecimal excess=order.getPaidAmount().subtract(order.getTotalAmount()).subtract(refunds.sumOverpaymentRefunds(id)).max(BigDecimal.ZERO).min(remaining);
        BigDecimal liability=returns.findByOrderIdOrderByIdDesc(id).stream().filter(r->r.getStatus().equals("RECEIVED")).map(ProductReturn::getRefundAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new Balances(order.getPaidAmount(),order.getRefundedAmount(),excess,liability,order.getTotalAmount().subtract(order.getPaidAmount()).max(BigDecimal.ZERO));
    }

    @Transactional(readOnly=true)
    public List<View> customerList(Long orderId,String session) { owned(orders.findById(orderId).orElseThrow(()->new ResourceNotFoundException("Order not found")),session);return returns.findByOrderIdOrderByIdDesc(orderId).stream().map(this::view).toList(); }
    @Transactional(readOnly=true)
    public Page<View> list(Pageable paging) { return returns.findAll(paging).map(this::view); }

    @Transactional
    public View request(Long orderId,String session,String key,Request command) {
        var order=lockedOrder(orderId);owned(order,session);
        if(order.getStatus()!=OrderStatus.DELIVERED||order.getAppointmentId()!=null||order.getServicePackageId()!=null)throw new BusinessException("Only delivered product orders accept return requests");
        if(order.getDeliveredAt()!=null&&order.getDeliveredAt().plus(Duration.ofDays(Math.max(0,returnWindowDays))).isBefore(Instant.now())) throw new BusinessException("The configured return request window has ended; contact customer care");
        if(command==null||command.reason()==null||command.reason().isBlank()||command.reason().length()>1000||command.lines()==null||command.lines().isEmpty()||command.lines().size()>100)throw new BusinessException("Return reason and item quantities required");
        var evidence=command.evidenceUrls()==null?List.<String>of():command.evidenceUrls();
        if(evidence.size()>8||evidence.stream().anyMatch(url->url==null||url.length()>1000||!url.matches("https?://[^\\s]+")))throw new BusinessException("Provide at most 8 valid http(s) evidence links");
        String requestKey=key==null?null:orderId+":"+key;
        if(requestKey!=null&&(key.isBlank()||requestKey.length()>100))throw new BusinessException("Invalid return request key");
        if(requestKey!=null) {
            var previous=returns.findByRequestKey(requestKey);
            if(previous.isPresent()) {var row=previous.get();if(!json(command.lines()).equals(row.getLinesJson())||!command.reason().equals(row.getReason())||!json(evidence).equals(row.getEvidenceJson()))throw new BusinessException("Request key reused with different data");return view(row);}
        }
        var prior=returns.findByOrderIdOrderByIdDesc(orderId);Set<Long> ids=new HashSet<>();BigDecimal selected=BigDecimal.ZERO;
        for(var line:command.lines()) {
            if(line==null||line.orderItemId()==null||line.quantity()<=0||!ids.add(line.orderItemId()))throw new BusinessException("Unique return items with positive quantities required");
            var item=order.getItems().stream().filter(i->i.getId().equals(line.orderItemId())).findFirst().orElseThrow(()->new BusinessException("Return item does not belong to this order"));
            int committed=prior.stream().filter(r->!r.getStatus().equals("REJECTED")).flatMap(r->lines(r).stream()).filter(l->l.orderItemId().equals(line.orderItemId())).mapToInt(Line::quantity).sum();
            if(line.quantity()>item.getQuantity()-committed)throw new BusinessException("Return quantity exceeds unreturned purchased quantity");
            selected=selected.add(item.getPrice().subtract(item.getDiscount()).multiply(BigDecimal.valueOf(line.quantity())));
        }
        BigDecimal amount=proratedProductRefund(order,selected);
        int previouslyRequested=prior.stream().filter(r->!r.getStatus().equals("REJECTED")).flatMap(r->lines(r).stream()).mapToInt(Line::quantity).sum();
        int nowRequested=command.lines().stream().mapToInt(Line::quantity).sum();
        if(previouslyRequested+nowRequested==order.getItems().stream().mapToInt(OrderItem::getQuantity).sum()) {
            BigDecimal committedAmount=prior.stream().filter(r->!r.getStatus().equals("REJECTED")).map(ProductReturn::getRefundAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
            amount=proratedProductRefund(order,order.getSubTotal()).subtract(committedAmount).max(BigDecimal.ZERO);
        }
        var row=new ProductReturn();row.setOrderId(orderId);row.setReason(command.reason());row.setRefundAmount(amount);row.setLinesJson(json(command.lines()));row.setEvidenceJson(json(evidence));row.setRequestKey(requestKey);
        return view(returns.saveAndFlush(row));
    }

    static BigDecimal proratedProductRefund(Order order,BigDecimal selected) {
        BigDecimal products=order.getTotalAmount().subtract(order.getShippingFee()).max(BigDecimal.ZERO).min(order.getSubTotal());
        return order.getSubTotal().signum()==0?BigDecimal.ZERO:selected.multiply(products).divide(order.getSubTotal(),0,RoundingMode.DOWN);
    }
    @Transactional
    public View decide(Long id,Decision command) {
        var order=orderForReturn(id);var row=lockedReturn(id);
        if(command==null||command.reason()==null||command.reason().isBlank()||command.reason().length()>1000)throw new BusinessException("Decision reason required");
        String target=command.approved()?"APPROVED":"REJECTED";
        if(row.getStatus().equals(target)&&command.reason().equals(row.getDecisionReason()))return view(row);
        if(!row.getStatus().equals("REQUESTED"))throw new BusinessException("Return request already decided");
        if(order.getStatus()!=OrderStatus.DELIVERED)throw new BusinessException("Order no longer supports this return");
        row.setStatus(target);row.setDecisionReason(command.reason());return view(row);
    }
    @Transactional
    public View receive(Long id,Confirmation command) {
        var order=orderForReturn(id);var row=lockedReturn(id);requireReference(command);
        if((row.getStatus().equals("RECEIVED")||row.getStatus().equals("REFUNDED"))&&command.reference().equals(row.getReceiptReference()))return view(row);
        if(!row.getStatus().equals("APPROVED"))throw new BusinessException("Return must be approved before goods are received");
        for(var line:lines(row)) {var item=order.getItems().stream().filter(i->i.getId().equals(line.orderItemId())).findFirst().orElseThrow();inventory.returnPartialStock(order.getOrderNumber(),item.getProductVariantId(),line.quantity());}
        row.setStatus("RECEIVED");row.setReceiptReference(command.reference());returns.flush();financialEvent(order);return view(row);
    }
    @Transactional
    public View refund(Long id,Confirmation command) {
        if(!SecurityUtils.isAdmin())throw new org.springframework.security.access.AccessDeniedException("Administrator required for refund confirmation");
        var order=orderForReturn(id);var row=lockedReturn(id);requireReference(command);
        if(row.getStatus().equals("REFUNDED")&&command.reference().equals(row.getRefundReference()))return view(row);
        if(!row.getStatus().equals("RECEIVED"))throw new BusinessException("Receive returned goods before refund confirmation");
        recordRefund(order,row.getRefundAmount(),command.reference(),command.reason(),"PRODUCT_RETURN",row.getId());row.setRefundReference(command.reference());row.setStatus("REFUNDED");returns.flush();financialEvent(order);return view(row);
    }
    @Transactional
    public void overpaymentRefund(Long orderId,BigDecimal amount,String reference,String reason) {
        if(!SecurityUtils.isAdmin())throw new org.springframework.security.access.AccessDeniedException("Administrator required");
        requireReference(new Confirmation(reference,reason));
        if(amount==null||amount.signum()<=0||amount.stripTrailingZeros().scale()>0)throw new BusinessException("Positive whole-VND refund amount required");
        var order=lockedOrder(orderId);
        var previous=refunds.findByReference(reference);if(previous.isPresent()){if(!previous.get().getOrderId().equals(orderId)||previous.get().getAmount().compareTo(amount)!=0||!"OVERPAYMENT".equals(previous.get().getRefundKind()))throw new BusinessException("Refund reference conflicts");return;}
        BigDecimal excess=order.getPaidAmount().subtract(order.getTotalAmount()).subtract(refunds.sumOverpaymentRefunds(orderId)).max(BigDecimal.ZERO);
        if(amount==null||amount.signum()<=0||amount.compareTo(excess)>0)throw new BusinessException("Amount exceeds the remaining overpayment");
        recordRefund(order,amount,reference,reason,"OVERPAYMENT",null);financialEvent(order);
    }
    @Transactional(readOnly=true) public void ensureWholeReturnAllowed(Long orderId) {
        if(returns.findByOrderIdOrderByIdDesc(orderId).stream().anyMatch(r->!r.getStatus().equals("REJECTED")))throw new BusinessException("Process existing item return requests instead of returning the whole order");
    }
    @Transactional public void replaceAllocation(Long orderId) {
        var order=lockedOrder(orderId);
        if(order.getStatus()!=OrderStatus.PENDING&&order.getStatus()!=OrderStatus.CONFIRMED&&order.getStatus()!=OrderStatus.PROCESSING)throw new BusinessException("Replace batches before shipment only");
        if(order.getItems()==null||order.getItems().isEmpty())throw new BusinessException("Only physical product orders support batch replacement");
        for(var item:order.getItems())inventory.replaceAllocation(order.getOrderNumber(),item.getProductVariantId(),item.getQuantity());
        order.getStatusHistories().add(OrderStatusHistory.builder().order(order).status(order.getStatus()).notes("Allocated stock replaced using currently sellable FEFO batches").build());
    }
    private void recordRefund(Order order,BigDecimal amount,String reference,String reason,String kind,Long returnId) {
        requireReference(new Confirmation(reference,reason));
        if(amount==null||amount.signum()<0||amount.stripTrailingZeros().scale()>0||amount.compareTo(order.getPaidAmount().subtract(order.getRefundedAmount()))>0)throw new BusinessException("Refund exceeds actual unrefunded receipts");
        if(amount.signum()>0) {
            if(refunds.findByReference(reference).isPresent())throw new BusinessException("Refund reference already used");
            var confirmation=new RefundConfirmation();confirmation.setOrderId(order.getId());confirmation.setReference(reference);confirmation.setAmount(amount);confirmation.setApprovalReason(reason);confirmation.setConfirmedByUserId(SecurityUtils.getCurrentUserId());confirmation.setRefundKind(kind);confirmation.setProductReturnId(returnId);refunds.saveAndFlush(confirmation);
            order.setRefundedAmount(order.getRefundedAmount().add(amount));order.setRefundReference(reference);
            if(order.getRefundedAmount().compareTo(order.getPaidAmount())==0)order.setPaymentStatus(PaymentStatus.REFUNDED);
        }
    }
    private void financialEvent(Order order) {
        BigDecimal returned=returns.findByOrderIdOrderByIdDesc(order.getId()).stream().filter(r->r.getStatus().equals("RECEIVED")||r.getStatus().equals("REFUNDED")).map(ProductReturn::getRefundAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal eligible=order.getTotalAmount().subtract(returned).max(BigDecimal.ZERO).min(order.getPaidAmount().subtract(order.getRefundedAmount()).max(BigDecimal.ZERO));
        events.publishEvent(new OrderEvents.OrderFinancialAdjustmentEvent(order.getId(),order.getUserId(),eligible));
    }
    private void requireReference(Confirmation c){if(c==null||c.reference()==null||c.reference().isBlank()||c.reference().length()>100||c.reason()==null||c.reason().isBlank()||c.reason().length()>250)throw new BusinessException("Stable reference and confirmation reason (max 250) required");}
    private Order lockedOrder(Long id){return orders.findByIdForUpdate(id).filter(o->!Boolean.TRUE.equals(o.getIsDeleted())).orElseThrow(()->new ResourceNotFoundException("Order not found"));}
    private Order orderForReturn(Long id){return lockedOrder(returns.findOrderId(id).orElseThrow(()->new ResourceNotFoundException("Return not found")));}
    private ProductReturn lockedReturn(Long id){return returns.lock(id).orElseThrow(()->new ResourceNotFoundException("Return not found"));}
    private void owned(Order order,String session) {
        if(SecurityUtils.isAdmin())return;
        if(order.getUserId()!=null&&SecurityUtils.getCurrentUserIdOptional().filter(order.getUserId()::equals).isPresent())return;
        if(order.getUserId()==null&&session!=null&&order.getGuestSessionId()!=null&&java.security.MessageDigest.isEqual(session.getBytes(java.nio.charset.StandardCharsets.UTF_8),order.getGuestSessionId().getBytes(java.nio.charset.StandardCharsets.UTF_8)))return;
        throw new org.springframework.security.access.AccessDeniedException("Order ownership required");
    }
    private List<Line> lines(ProductReturn row){try{return mapper.readValue(row.getLinesJson(),new TypeReference<List<Line>>(){});}catch(Exception e){throw new IllegalStateException(e);}}
    private String json(Object data){try{return mapper.writeValueAsString(data);}catch(Exception e){throw new IllegalArgumentException(e);}}
    private View view(ProductReturn row){try{return new View(row.getId(),row.getOrderId(),row.getStatus(),row.getReason(),row.getDecisionReason(),row.getRefundAmount(),lines(row),mapper.readValue(row.getEvidenceJson(),new TypeReference<List<String>>(){}),row.getCreatedAt(),row.getReplacementOrderId(),row.getReplacementReason(),row.getReplacementLinkedAt());}catch(Exception e){throw new IllegalStateException(e);}}
}
