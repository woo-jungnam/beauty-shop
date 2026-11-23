package com.core.beautyshop.modules.identity.application.service;

import com.core.beautyshop.modules.identity.domain.*;
import com.core.beautyshop.modules.identity.domain.enums.MembershipTier;
import com.core.beautyshop.shared.exception.BusinessException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;

@Service @RequiredArgsConstructor
public class LoyaltyReconciliationService {
    private static final BigDecimal SPEND_PER_POINT = BigDecimal.valueOf(10000);
    private static final String SPEND_COLUMNS = """
        o.user_id, o.total_amount, o.paid_amount, o.refunded_amount, o.shipping_fee,
        o.status, o.service_package_id, o.appointment_id,
        CASE WHEN o.service_package_id IS NULL AND o.appointment_id IS NULL THEN
            (SELECT COALESCE(SUM(r.refund_amount), 0) FROM product_returns r
             WHERE r.order_id = o.id AND r.status IN ('RECEIVED', 'REFUNDED'))
        ELSE 0 END AS returned_amount
        """;
    private final UserRepository users;
    private final LoyaltyPointAwardRepository awards;
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    @Transactional public void reconcile(Long orderId,Long userId,BigDecimal requested){
        if(orderId==null||userId==null)return;
        em.flush();
        var rows = jdbc.query("SELECT " + SPEND_COLUMNS + " FROM orders o WHERE o.id=? AND o.is_deleted=false",
                (rs, n) -> readSpend(rs), orderId);
        if(rows.isEmpty()||!userId.equals(rows.getFirst().owner()))return;
        Spend spend=rows.getFirst();
        BigDecimal eligible = spend.eligibleAmount(requested);
        int target=eligible.divideToIntegralValue(SPEND_PER_POINT).intValueExact();
        var user=users.findByIdForUpdate(userId).orElseThrow(()->new BusinessException("Không tìm thấy chủ điểm thành viên"));
        var award=awards.findByOrderId(orderId).orElse(null);
        int old=award==null||award.isReversed()?0:award.getPoints();
        if (award == null && !spend.eligibleState()) return;
        if (old == target && award != null && award.getEligibleAmount() != null
                && award.getEligibleAmount().compareTo(eligible) == 0) return;
        if (old != target) {
            long calculated = (long) (user.getLoyaltyPoints() == null ? 0 : user.getLoyaltyPoints()) + target - old;
            if (calculated > Integer.MAX_VALUE) throw new BusinessException("Điểm thành viên vượt giới hạn");
            int points = (int) Math.max(0, calculated);
            user.setLoyaltyPoints(points);
            user.setMembershipTier(Arrays.stream(MembershipTier.values())
                    .filter(tier -> points >= tier.getRequiredPoints())
                    .max(Comparator.comparingInt(MembershipTier::getRequiredPoints))
                    .orElse(MembershipTier.MEMBER));
            users.save(user);
        }
        if (award == null) award = LoyaltyPointAward.builder().userId(userId).orderId(orderId).build();
        award.setPoints(target);
        award.setReversed(eligible.signum() == 0);
        award.setEligibleAmount(eligible);
        awards.save(award);
    }

    @Transactional(readOnly = true)
    public BigDecimal eligibleSpent(Long userId) {
        var amounts = jdbc.query("SELECT " + SPEND_COLUMNS + """
                , a.eligible_amount, a.points, a.reversed
                FROM orders o
                LEFT JOIN loyalty_point_awards a ON a.order_id = o.id AND a.user_id = o.user_id AND a.is_deleted = false
                WHERE o.user_id = ? AND o.is_deleted = false
                  AND (o.status IN ('DELIVERED', 'COMPLETED') OR o.service_package_id IS NOT NULL)
                """, (rs, n) -> {
            Spend spend = readSpend(rs);
            if (rs.getBoolean("reversed") || !spend.eligibleState()) return BigDecimal.ZERO;
            BigDecimal recorded = rs.getBigDecimal("eligible_amount");
            if (recorded != null) return recorded;
            return spend.legacyEligibleAmount(rs.getInt("points"));
        }, userId);
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Spend readSpend(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Spend(rs.getObject("user_id", Long.class), rs.getBigDecimal("total_amount"),
                rs.getBigDecimal("paid_amount"), rs.getBigDecimal("refunded_amount"),
                rs.getBigDecimal("shipping_fee"), rs.getBigDecimal("returned_amount"), rs.getString("status"),
                rs.getObject("service_package_id") != null, rs.getObject("appointment_id") != null);
    }

    private record Spend(Long owner, BigDecimal total, BigDecimal paid, BigDecimal refunded,
                         BigDecimal shipping, BigDecimal returned, String status, boolean packageOrder, boolean visit) {
        boolean eligibleState() {
            if (packageOrder) return !"CANCELLED".equals(status);
            return "COMPLETED".equals(status) || (!visit && "DELIVERED".equals(status));
        }

        BigDecimal netPaid() {
            return paid.subtract(refunded).max(BigDecimal.ZERO);
        }

        BigDecimal eligibleAmount(BigDecimal requested) {
            if (!eligibleState()) return BigDecimal.ZERO;
            BigDecimal base = requested == null ? total : requested.max(BigDecimal.ZERO);
            if (!packageOrder && !visit) base = base.subtract(shipping).max(BigDecimal.ZERO);
            BigDecimal capacity = total.subtract(shipping).subtract(returned).max(BigDecimal.ZERO);
            return base.min(capacity).min(netPaid());
        }

        BigDecimal legacyEligibleAmount(int awardedPoints) {
            if (packageOrder) {
                // Historical packages only retained rounded points from the Spa progress event.
                return eligibleAmount(SPEND_PER_POINT.multiply(BigDecimal.valueOf(awardedPoints)));
            }
            BigDecimal requested = total.subtract(refunded.max(returned));
            if (refunded.signum() > 0 || returned.signum() > 0) requested = requested.min(netPaid());
            return eligibleAmount(requested);
        }
    }
}
