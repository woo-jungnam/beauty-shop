package com.core.beautyshop.modules.identity.application.listener;
import com.core.beautyshop.modules.identity.application.service.UserService;
import com.core.beautyshop.modules.order.api.event.OrderEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component @RequiredArgsConstructor
public class IdentityOrderEventListener {
    private final UserService userService;
    private final com.core.beautyshop.modules.identity.application.service.LoyaltyReconciliationService loyalty;
    @EventListener
    public void handleOrderDelivered(OrderEvents.OrderDeliveredEvent event) {
        if (event.getUserId() == null || event.getTotalAmount() == null) return;
        loyalty.reconcile(event.getOrderId(),event.getUserId(),event.getTotalAmount());
    }
    @EventListener
    public void handleOrderReturned(OrderEvents.OrderReturnedEvent event) {
        if (event.getUserId() != null) userService.reverseLoyaltyPoints(event.getUserId(), event.getOrderId());
    }
    @EventListener public void financialChange(OrderEvents.OrderFinancialAdjustmentEvent event){
        loyalty.reconcile(event.orderId(),event.userId(),event.netEligibleAmount());
    }
    @EventListener public void spaProgress(com.core.beautyshop.modules.identity.api.event.LoyaltyEvents.EligibleSpendChanged event){
        loyalty.reconcile(event.orderId(),event.userId(),event.eligibleAmount());
    }
    @EventListener public void paidVisit(OrderEvents.OrderPaidEvent event){
        if(event.getUserId()==null || event.getServicePackageId()!=null)return;
        loyalty.reconcile(event.getOrderId(),event.getUserId(),event.getTotalAmount());
    }
}
