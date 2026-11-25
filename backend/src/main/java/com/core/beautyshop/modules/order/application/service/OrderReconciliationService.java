package com.core.beautyshop.modules.order.application.service;

import com.core.beautyshop.modules.order.domain.Order;
import com.core.beautyshop.modules.order.domain.OrderRepository;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.modules.payment.api.PaymentFacade;
import com.core.beautyshop.modules.payment.api.dto.CodSettlementTransactionDto;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Aggregates COD reconciliation metrics, shipper debt rollups, and settlement history. */
@Service
@RequiredArgsConstructor
public class OrderReconciliationService {

    private final OrderRepository orderRepository;
    private final PaymentFacade paymentFacade;

    public record ShipperReconciliationSummary(
            String channelKey,
            Long shipperId,
            boolean carrierChannel,
            String label,
            String phone,
            long pendingOrdersCount,
            long settledOrdersCount,
            long inTransitOrdersCount,
            BigDecimal collectedAmount,
            BigDecimal remittedAmount,
            BigDecimal pendingAmount,
            BigDecimal inTransitAmount
    ) {}

    public record OrderReconciliationSummary(
            long pendingCodOrders,
            BigDecimal pendingCodAmount,
            long settledCodOrders,
            BigDecimal settledCodAmount,
            long inTransitCodOrders,
            BigDecimal inTransitCodAmount,
            BigDecimal shipperPendingCodAmount,
            BigDecimal carrierPendingCodAmount,
            long attentionOrdersCount,
            List<ShipperReconciliationSummary> byShipper
    ) {}

    public record CodSettlementRecord(
            Long id,
            Long orderId,
            String orderNumber,
            String referenceCode,
            BigDecimal amount,
            String content,
            Instant createdAt,
            Long shipperId,
            String shipperName,
            String carrierName,
            String customerName
    ) {}

    @Transactional(readOnly = true)
    public OrderReconciliationSummary getSummary(Instant from, Instant to, Long shipperId, Boolean carrierOnly, String keyword) {
        Specification<Order> codSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            predicates.add(OrderReconciliationPredicates.codReconciliationMatches(root, cb, "ALL"));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) predicates.add(cb.lessThan(root.get("createdAt"), to));
            if (shipperId != null) {
                predicates.add(cb.equal(root.get("shipperId"), shipperId));
            } else if (Boolean.TRUE.equals(carrierOnly)) {
                predicates.add(cb.isNull(root.get("shipperId")));
            } else if (Boolean.FALSE.equals(carrierOnly)) {
                predicates.add(cb.isNotNull(root.get("shipperId")));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(OrderReconciliationPredicates.keywordMatches(root, cb, keyword));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        List<Order> codOrders = orderRepository.findAll(codSpec);

        Specification<Order> attentionSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            predicates.add(OrderReconciliationPredicates.attentionNeeded(root, query, cb));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) predicates.add(cb.lessThan(root.get("createdAt"), to));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        long attentionCount = orderRepository.count(attentionSpec);

        long pendingCodOrders = 0;
        BigDecimal pendingCodAmount = BigDecimal.ZERO;
        long settledCodOrders = 0;
        BigDecimal settledCodAmount = BigDecimal.ZERO;
        long inTransitCodOrders = 0;
        BigDecimal inTransitCodAmount = BigDecimal.ZERO;
        BigDecimal shipperPendingCodAmount = BigDecimal.ZERO;
        BigDecimal carrierPendingCodAmount = BigDecimal.ZERO;

        Map<String, ChannelAccumulator> channelMap = new LinkedHashMap<>();

        for (Order order : codOrders) {
            boolean isShipperChannel = order.getShipperId() != null;
            String key = isShipperChannel
                    ? "SHIPPER-" + order.getShipperId()
                    : "CARRIER-" + (isBlank(order.getCarrierName()) ? "DEFAULT" : order.getCarrierName().trim().toUpperCase());
            String label = isShipperChannel
                    ? (!isBlank(order.getShipperName()) ? order.getShipperName().trim() : "Tài xế #" + order.getShipperId())
                    : (!isBlank(order.getCarrierName()) ? "Hãng VC: " + order.getCarrierName().trim() : "Đơn vị vận chuyển / Tự giao");

            ChannelAccumulator acc = channelMap.computeIfAbsent(key, ignored ->
                    new ChannelAccumulator(key, order.getShipperId(), !isShipperChannel, label, order.getShipperPhone()));

            if (order.getStatus() == OrderStatus.SHIPPED
                    || order.getStatus() == OrderStatus.DELIVERY_FAILED
                    || order.getStatus() == OrderStatus.RETURNING_TO_SENDER) {
                BigDecimal expected = nonNeg(order.getTotalAmount()).subtract(nonNeg(order.getPaidAmount())).max(BigDecimal.ZERO);
                inTransitCodOrders++;
                inTransitCodAmount = inTransitCodAmount.add(expected);
                acc.inTransitOrdersCount++;
                acc.inTransitAmount = acc.inTransitAmount.add(expected);
                continue;
            }

            BigDecimal pending = order.unsettledCod();
            BigDecimal collected = order.hasShipperCodCollection()
                    ? nonNeg(order.getShipperCodCollected())
                    : (order.getStatus() == OrderStatus.DELIVERED ? nonNeg(order.getTotalAmount()) : BigDecimal.ZERO);
            BigDecimal remitted = order.hasShipperCodCollection()
                    ? nonNeg(order.getShipperCodRemitted()).min(collected)
                    : (order.getStatus() == OrderStatus.DELIVERED ? nonNeg(order.getPaidAmount()).min(collected) : BigDecimal.ZERO);

            acc.collectedAmount = acc.collectedAmount.add(collected);
            acc.remittedAmount = acc.remittedAmount.add(remitted);
            acc.pendingAmount = acc.pendingAmount.add(pending);

            settledCodAmount = settledCodAmount.add(remitted);
            if (pending.signum() > 0) {
                pendingCodOrders++;
                pendingCodAmount = pendingCodAmount.add(pending);
                acc.pendingOrdersCount++;
                if (isShipperChannel) {
                    shipperPendingCodAmount = shipperPendingCodAmount.add(pending);
                } else {
                    carrierPendingCodAmount = carrierPendingCodAmount.add(pending);
                }
            } else if (collected.signum() > 0) {
                settledCodOrders++;
                acc.settledOrdersCount++;
            }
        }

        List<ShipperReconciliationSummary> byShipper = channelMap.values().stream()
                .map(ChannelAccumulator::toSummary)
                .sorted(Comparator
                        .comparing(ShipperReconciliationSummary::pendingAmount).reversed()
                        .thenComparing(ShipperReconciliationSummary::inTransitAmount, Comparator.reverseOrder())
                        .thenComparing(ShipperReconciliationSummary::remittedAmount, Comparator.reverseOrder()))
                .toList();

        return new OrderReconciliationSummary(
                pendingCodOrders,
                pendingCodAmount,
                settledCodOrders,
                settledCodAmount,
                inTransitCodOrders,
                inTransitCodAmount,
                shipperPendingCodAmount,
                carrierPendingCodAmount,
                attentionCount,
                byShipper
        );
    }

    @Transactional(readOnly = true)
    public Page<CodSettlementRecord> getSettlementHistory(String keyword, Instant from, Instant to, Pageable pageable) {
        Page<CodSettlementTransactionDto> page = paymentFacade.searchCodSettlements(keyword, from, to, pageable);
        List<String> orderNumbers = page.getContent().stream()
                .map(CodSettlementTransactionDto::orderNumber)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, Order> orderByNumber = new LinkedHashMap<>();
        if (!orderNumbers.isEmpty()) {
            for (Order order : orderRepository.findByOrderNumberInAndIsDeletedFalse(orderNumbers)) {
                orderByNumber.put(order.getOrderNumber(), order);
            }
        }
        return page.map(tx -> {
            Order order = orderByNumber.get(tx.orderNumber());
            return new CodSettlementRecord(
                    tx.id(),
                    order != null ? order.getId() : null,
                    tx.orderNumber(),
                    tx.referenceCode(),
                    tx.amount(),
                    tx.content(),
                    tx.createdAt(),
                    order != null ? order.getShipperId() : null,
                    order != null ? order.getShipperName() : null,
                    order != null ? order.getCarrierName() : null,
                    order != null ? order.getCustomerName() : null
            );
        });
    }

    private static BigDecimal nonNeg(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static final class ChannelAccumulator {
        final String channelKey;
        final Long shipperId;
        final boolean carrierChannel;
        final String label;
        final String phone;
        long pendingOrdersCount;
        long settledOrdersCount;
        long inTransitOrdersCount;
        BigDecimal collectedAmount = BigDecimal.ZERO;
        BigDecimal remittedAmount = BigDecimal.ZERO;
        BigDecimal pendingAmount = BigDecimal.ZERO;
        BigDecimal inTransitAmount = BigDecimal.ZERO;

        ChannelAccumulator(String channelKey, Long shipperId, boolean carrierChannel, String label, String phone) {
            this.channelKey = channelKey;
            this.shipperId = shipperId;
            this.carrierChannel = carrierChannel;
            this.label = label;
            this.phone = phone;
        }

        ShipperReconciliationSummary toSummary() {
            return new ShipperReconciliationSummary(
                    channelKey,
                    shipperId,
                    carrierChannel,
                    label,
                    phone,
                    pendingOrdersCount,
                    settledOrdersCount,
                    inTransitOrdersCount,
                    collectedAmount,
                    remittedAmount,
                    pendingAmount,
                    inTransitAmount
            );
        }
    }
}

