package com.core.beautyshop.modules.spa.application.service;
import com.core.beautyshop.modules.identity.api.event.LoyaltyEvents;
import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.treatment.TreatmentEntities.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

@Service @RequiredArgsConstructor
public class SpaLoyaltyService {
    private final EntityManager em;
    private final ApplicationEventPublisher events;
    @Transactional public void performedTicket(UserServiceTicket ticket){
        if(ticket==null || ticket.getOrderId()==null)return;
        em.flush();
        var snapshot=em.find(SpaPurchaseSnapshot.class,ticket.getOrderId());
        int total=snapshot==null?ticket.getTotalSessions():snapshot.getEntitlements().values().stream().mapToInt(Integer::intValue).sum();
        if(total<=0)return;
        long performed=em.createQuery("select count(i) from AppointmentItem i where i.ticket.id=:id and i.isDeleted=false and i.executionStatus=com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.PERFORMED and i.ticketUsageState=com.core.beautyshop.modules.spa.domain.enums.TicketUsageState.REDEEMED",Long.class).setParameter("id",ticket.getId()).getSingleResult();
        BigDecimal price=(BigDecimal)em.createNativeQuery("select total_amount from orders where id=:id").setParameter("id",ticket.getOrderId()).getSingleResult();
        BigDecimal eligible=price.multiply(BigDecimal.valueOf(Math.min(performed,total))).divide(BigDecimal.valueOf(total),2,RoundingMode.DOWN);
        events.publishEvent(new LoyaltyEvents.EligibleSpendChanged(ticket.getOrderId(),ticket.getUserId(),eligible));
    }
    @Transactional public void performedCourse(Course course){
        em.flush();
        BigDecimal remaining=em.createQuery("select coalesce(sum(f.amount),0) from TreatmentFinancialConsumption f where f.courseId=:id and f.kind='PERFORMED'",BigDecimal.class).setParameter("id",course.getId()).getSingleResult();
        var orderIds=new ArrayList<Long>();orderIds.add(course.getOrderId());
        orderIds.addAll(em.createQuery("select c.settlementOrderId from TreatmentChange c where c.courseId=:id and c.status='APPLIED' and c.settlementOrderId is not null order by c.id",Long.class).setParameter("id",course.getId()).getResultList());
        for(Long id:orderIds){
            BigDecimal capacity=(BigDecimal)em.createNativeQuery("select greatest(0,least(total_amount,paid_amount-refunded_amount)) from orders where id=:id").setParameter("id",id).getSingleResult();
            BigDecimal eligible=remaining.min(capacity);remaining=remaining.subtract(eligible).max(BigDecimal.ZERO);
            events.publishEvent(new LoyaltyEvents.EligibleSpendChanged(id,course.getUserId(),eligible));
        }
    }
}
