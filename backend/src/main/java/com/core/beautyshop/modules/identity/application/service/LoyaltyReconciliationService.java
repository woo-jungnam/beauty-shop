package com.core.beautyshop.modules.identity.application.service;

import com.core.beautyshop.modules.identity.domain.*;
import com.core.beautyshop.modules.identity.domain.enums.MembershipTier;
import com.core.beautyshop.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;

@Service @RequiredArgsConstructor
public class LoyaltyReconciliationService {
    private final UserRepository users;
    private final LoyaltyPointAwardRepository awards;
    private final JdbcTemplate jdbc;
    private final jakarta.persistence.EntityManager em;
    @Transactional public void reconcile(Long orderId,Long userId,BigDecimal requested){
        if(orderId==null||userId==null)return;
        em.flush();
        var rows=jdbc.query("select user_id,total_amount,paid_amount,refunded_amount,shipping_fee,status,service_package_id,appointment_id from orders where id=? and is_deleted=false",
            (rs,n)->new Spend(rs.getObject("user_id")==null?null:rs.getLong("user_id"),rs.getBigDecimal("total_amount"),rs.getBigDecimal("paid_amount").subtract(rs.getBigDecimal("refunded_amount")).max(BigDecimal.ZERO),rs.getBigDecimal("shipping_fee"),rs.getString("status"),rs.getObject("service_package_id")!=null,rs.getObject("appointment_id")!=null),orderId);
        if(rows.isEmpty()||!userId.equals(rows.getFirst().owner()))return;
        Spend spend=rows.getFirst();
        boolean eligibleState=spend.packageOrder() ? !"CANCELLED".equals(spend.status()) : spend.visit() ? "COMPLETED".equals(spend.status()) : "DELIVERED".equals(spend.status());
        BigDecimal base=requested==null?spend.total():requested.max(BigDecimal.ZERO);
        if(!spend.packageOrder()&&!spend.visit())base=base.subtract(spend.shipping()).max(BigDecimal.ZERO);
        BigDecimal returned = spend.packageOrder() || spend.visit() ? BigDecimal.ZERO : jdbc.queryForObject(
                "select coalesce(sum(refund_amount),0) from product_returns where order_id=? and status in ('RECEIVED','REFUNDED')",BigDecimal.class,orderId);
        BigDecimal capacity=spend.total().subtract(spend.shipping()).subtract(returned).max(BigDecimal.ZERO);
        BigDecimal eligible=eligibleState?base.min(capacity).min(spend.netPaid()):BigDecimal.ZERO;
        int target=eligible.divideToIntegralValue(BigDecimal.valueOf(10000)).intValueExact();
        var user=users.findByIdForUpdate(userId).orElseThrow(()->new BusinessException("Không tìm thấy chủ điểm thành viên"));
        var award=awards.findByOrderId(orderId).orElse(null);
        int old=award==null||award.isReversed()?0:award.getPoints();
        if(old==target)return;
        long calculated=(long)(user.getLoyaltyPoints()==null?0:user.getLoyaltyPoints())+target-old;
        if(calculated>Integer.MAX_VALUE)throw new BusinessException("Điểm thành viên vượt giới hạn");
        int points=(int)Math.max(0,calculated);user.setLoyaltyPoints(points);
        user.setMembershipTier(Arrays.stream(MembershipTier.values()).filter(t->points>=t.getRequiredPoints()).max(Comparator.comparingInt(MembershipTier::getRequiredPoints)).orElse(MembershipTier.MEMBER));
        if(award==null)award=LoyaltyPointAward.builder().userId(userId).orderId(orderId).points(target).build();
        award.setPoints(target);award.setReversed(target==0);awards.save(award);users.save(user);
    }
    private record Spend(Long owner,BigDecimal total,BigDecimal netPaid,BigDecimal shipping,String status,boolean packageOrder,boolean visit){}
}
