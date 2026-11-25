package com.core.beautyshop.modules.order.application.service;

import com.core.beautyshop.modules.order.domain.Order;
import com.core.beautyshop.modules.order.domain.ProductReturn;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.modules.order.domain.enums.PaymentStatus;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import java.math.BigDecimal;
import java.util.Locale;

/** Shared JPA Criteria predicates for admin order search and COD reconciliation. */
final class OrderReconciliationPredicates {

    private OrderReconciliationPredicates() {}

    static Predicate keywordMatches(Root<Order> root, CriteriaBuilder cb, String keyword) {
        String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        return cb.or(
                cb.like(cb.lower(cb.coalesce(root.<String>get("orderNumber"), "")), pattern),
                cb.like(cb.lower(cb.coalesce(root.<String>get("customerName"), "")), pattern),
                cb.like(cb.lower(cb.coalesce(root.<String>get("customerPhone"), "")), pattern),
                cb.like(cb.lower(cb.coalesce(root.<String>get("shipperName"), "")), pattern),
                cb.like(cb.lower(cb.coalesce(root.<String>get("shipperPhone"), "")), pattern),
                cb.like(cb.lower(cb.coalesce(root.<String>get("trackingCode"), "")), pattern)
        );
    }

    static Predicate physicalCodOrder(Root<Order> root, CriteriaBuilder cb) {
        return cb.and(
                cb.equal(root.get("paymentMethod"), PaymentMethod.COD),
                cb.isNull(root.get("servicePackageId")),
                cb.isNull(root.get("appointmentId"))
        );
    }

    static Predicate codReconciliationMatches(Root<Order> root, CriteriaBuilder cb, String mode) {
        String normalized = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
        Predicate baseCod = physicalCodOrder(root, cb);
        Expression<BigDecimal> collected = cb.coalesce(root.<BigDecimal>get("shipperCodCollected"), BigDecimal.ZERO);
        Expression<BigDecimal> remitted = cb.coalesce(root.<BigDecimal>get("shipperCodRemitted"), BigDecimal.ZERO);
        Predicate hasShipperCollection = cb.greaterThan(collected, BigDecimal.ZERO);
        Predicate noShipperCollection = cb.lessThanOrEqualTo(collected, BigDecimal.ZERO);

        Predicate pendingCod = cb.or(
                cb.and(
                        hasShipperCollection,
                        root.get("status").in(OrderStatus.DELIVERED, OrderStatus.RETURNED),
                        cb.greaterThan(collected, remitted)
                ),
                cb.and(
                        noShipperCollection,
                        cb.equal(root.get("status"), OrderStatus.DELIVERED),
                        cb.greaterThan(root.<BigDecimal>get("totalAmount"), root.<BigDecimal>get("paidAmount"))
                )
        );

        Predicate settledCod = cb.or(
                cb.and(
                        hasShipperCollection,
                        root.get("status").in(OrderStatus.DELIVERED, OrderStatus.RETURNED),
                        cb.greaterThanOrEqualTo(remitted, collected)
                ),
                cb.and(
                        noShipperCollection,
                        cb.equal(root.get("status"), OrderStatus.DELIVERED),
                        cb.greaterThanOrEqualTo(root.<BigDecimal>get("paidAmount"), root.<BigDecimal>get("totalAmount"))
                )
        );

        Predicate inTransitCod = root.get("status").in(
                OrderStatus.SHIPPED,
                OrderStatus.DELIVERY_FAILED,
                OrderStatus.RETURNING_TO_SENDER
        );

        return switch (normalized) {
            case "PENDING" -> cb.and(baseCod, pendingCod);
            case "SETTLED" -> cb.and(baseCod, settledCod);
            case "IN_TRANSIT" -> cb.and(baseCod, inTransitCod);
            default -> cb.and(baseCod, cb.or(pendingCod, settledCod, inTransitCod));
        };
    }

    static Predicate attentionNeeded(Root<Order> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Long> activeReturnSubquery = query.subquery(Long.class);
        Root<ProductReturn> returnRoot = activeReturnSubquery.from(ProductReturn.class);
        activeReturnSubquery.select(returnRoot.get("id")).where(
                cb.equal(returnRoot.get("orderId"), root.get("id")),
                returnRoot.get("status").in("REQUESTED", "APPROVED", "RECEIVED")
        );
        Predicate hasActiveProductReturn = cb.exists(activeReturnSubquery);
        Predicate refundPending = cb.equal(root.get("paymentStatus"), PaymentStatus.REFUND_PENDING);
        Predicate wholeOrderNeedsRefund = cb.and(
                root.get("paymentStatus").in(PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED),
                root.get("status").in(
                        OrderStatus.DELIVERY_FAILED,
                        OrderStatus.RETURNING_TO_SENDER,
                        OrderStatus.RETURN_TO_SENDER,
                        OrderStatus.RETURNED,
                        OrderStatus.CANCELLED
                )
        );
        return cb.or(hasActiveProductReturn, refundPending, wholeOrderNeedsRefund);
    }
}

