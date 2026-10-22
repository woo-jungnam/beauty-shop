package com.core.beautyshop.modules.order.application.service;
import com.core.beautyshop.modules.order.domain.*;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.order.api.event.OrderEvents;
import com.core.beautyshop.modules.payment.api.PaymentFacade;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import com.core.beautyshop.shared.exception.*;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import java.math.BigDecimal;
@Service @RequiredArgsConstructor
public class OrderCodSettlementService {
    private final OrderRepository orders;
    private final PaymentFacade payments;
    private final OrderFacade facade;
    private final ApplicationEventPublisher events;
    public record Settlement(BigDecimal amount,String reference,String reason){}
    @Transactional
    public void confirm(Long id,Settlement c){
        if(c==null||c.amount()==null||c.amount().signum()<=0||c.amount().stripTrailingZeros().scale()>0||c.reference()==null||c.reference().isBlank()||c.reference().length()>80||c.reason()==null||c.reason().isBlank()||c.reason().length()>250)throw new BusinessException("Whole-VND collection amount, bank reference and reason required");
        var order=orders.findByIdForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Order not found"));
        String reference="COD-SETTLED-"+c.reference();
        if(order.getPaymentMethod()!=PaymentMethod.COD||order.getStatus()!=OrderStatus.DELIVERED)throw new BusinessException("Only delivered COD orders can be settled");
        boolean created=payments.recordCarrierRemittance(order.getOrderNumber(),c.amount(),reference,c.reason(),SecurityUtils.getCurrentUserId());if(!created)return;
        if(order.getPaymentMethod()!=PaymentMethod.COD||order.getStatus()!=OrderStatus.DELIVERED||c.amount().compareTo(order.getTotalAmount().subtract(order.getPaidAmount()))>0)throw new BusinessException("Only outstanding delivered COD amounts can be settled");
        facade.markOrderAsPaid(order.getOrderNumber(),c.amount(),reference);
        events.publishEvent(new OrderEvents.OrderFinancialAdjustmentEvent(id,order.getUserId(),order.getPaidAmount().subtract(order.getRefundedAmount()).min(order.getTotalAmount()).max(BigDecimal.ZERO)));
    }
}
