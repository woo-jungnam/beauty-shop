package com.core.beautyshop.modules.procurement.application;

import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import com.core.beautyshop.modules.procurement.domain.ProcurementMovement;
import com.core.beautyshop.modules.procurement.domain.ProcurementMovementRepository;
import com.core.beautyshop.modules.procurement.domain.PurchaseOrder;
import com.core.beautyshop.modules.procurement.domain.PurchaseOrderItem;
import com.core.beautyshop.modules.procurement.domain.PurchaseOrderRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Provides paginated procurement receipt/payment movement history and summary rollups. */
@Service
@RequiredArgsConstructor
public class ProcurementHistoryService {

    private final ProcurementMovementRepository movements;
    private final PurchaseOrderRepository orders;
    private final CatalogFacade catalog;
    private final ObjectMapper objectMapper;

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StoredLine(Long itemId, Integer quantity, String batchCode, LocalDate expirationDate) {}

    public record MovementLineView(
            Long itemId,
            Long variantId,
            String productName,
            String variantName,
            String sku,
            Integer quantity,
            BigDecimal unitCost,
            BigDecimal lineAmount,
            String batchCode,
            LocalDate expirationDate
    ) {}

    public record ProcurementMovementHistoryView(
            Long id,
            Long purchaseOrderId,
            String orderNumber,
            PurchaseOrder.Status orderStatus,
            Long supplierId,
            String supplierCode,
            String supplierName,
            Long warehouseId,
            String kind,
            String reference,
            BigDecimal amount,
            String reason,
            Instant createdAt,
            String createdBy,
            int totalQuantity,
            List<MovementLineView> lines
    ) {}

    public record ProcurementHistorySummary(
            long receiptCount,
            BigDecimal totalReceiptAmount,
            long supplierReturnCount,
            BigDecimal totalReturnAmount,
            BigDecimal totalPaymentAmount,
            BigDecimal totalRefundAmount,
            BigDecimal netPaidAmount,
            long receivedOrdersCount,
            BigDecimal receivedOrdersValue,
            BigDecimal outstandingPayableAmount
    ) {}

    @Transactional(readOnly = true)
    public Page<ProcurementMovementHistoryView> searchMovements(
            String keyword,
            String kind,
            Long supplierId,
            Long warehouseId,
            Long purchaseOrderId,
            Instant from,
            Instant to,
            Pageable pageable) {

        List<Long> scopeOrderIds = null;
        if (supplierId != null || warehouseId != null) {
            scopeOrderIds = findMatchingOrderIds(null, supplierId, warehouseId);
            if (scopeOrderIds.isEmpty()) {
                return Page.empty(pageable);
            }
        }

        List<Long> keywordOrderIds = (keyword != null && !keyword.isBlank())
                ? findMatchingOrderIds(keyword, null, null)
                : List.of();

        List<Long> finalScopeIds = scopeOrderIds;
        Specification<ProcurementMovement> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (kind != null && !kind.isBlank()) {
                String normalizedKind = kind.trim().toUpperCase(Locale.ROOT);
                if ("CASHFLOW".equals(normalizedKind)) {
                    predicates.add(root.get("kind").in("PAYMENT", "SUPPLIER_REFUND"));
                } else {
                    predicates.add(cb.equal(root.get("kind"), normalizedKind));
                }
            }
            if (purchaseOrderId != null) {
                predicates.add(cb.equal(root.get("purchaseOrderId"), purchaseOrderId));
            }
            if (finalScopeIds != null) {
                predicates.add(root.get("purchaseOrderId").in(finalScopeIds));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                Predicate textMatch = cb.or(
                        cb.like(cb.lower(cb.coalesce(root.<String>get("reference"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("reason"), "")), pattern)
                );
                if (!keywordOrderIds.isEmpty()) {
                    predicates.add(cb.or(textMatch, root.get("purchaseOrderId").in(keywordOrderIds)));
                } else {
                    predicates.add(textMatch);
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<ProcurementMovement> page = movements.findAll(spec, pageable);
        if (page.isEmpty()) {
            return page.map(ignored -> null);
        }

        Set<Long> poIds = new LinkedHashSet<>();
        for (ProcurementMovement m : page.getContent()) {
            if (m.getPurchaseOrderId() != null) poIds.add(m.getPurchaseOrderId());
        }

        Map<Long, PurchaseOrder> orderMap = new HashMap<>();
        Map<Long, PurchaseOrderItem> itemMap = new HashMap<>();
        Set<Long> variantIds = new LinkedHashSet<>();
        for (PurchaseOrder po : orders.findAllById(poIds)) {
            orderMap.put(po.getId(), po);
            if (po.getItems() != null) {
                for (PurchaseOrderItem item : po.getItems()) {
                    itemMap.put(item.getId(), item);
                    if (item.getProductVariantId() != null) {
                        variantIds.add(item.getProductVariantId());
                    }
                }
            }
        }

        Map<Long, ProductVariantSummaryDto> variants = variantIds.isEmpty()
                ? Map.of()
                : catalog.getVariantSummariesForInventory(variantIds);

        return page.map(m -> toHistoryView(m, orderMap.get(m.getPurchaseOrderId()), itemMap, variants));
    }

    @Transactional(readOnly = true)
    public ProcurementHistorySummary getHistorySummary(
            String keyword,
            Long supplierId,
            Long warehouseId,
            Instant from,
            Instant to) {

        List<Long> scopeOrderIds = null;
        if (supplierId != null || warehouseId != null) {
            scopeOrderIds = findMatchingOrderIds(null, supplierId, warehouseId);
        }
        List<Long> keywordOrderIds = (keyword != null && !keyword.isBlank())
                ? findMatchingOrderIds(keyword, null, null)
                : List.of();

        List<ProcurementMovement> matchedMovements;
        if (scopeOrderIds != null && scopeOrderIds.isEmpty()) {
            matchedMovements = List.of();
        } else {
            List<Long> finalScopeIds = scopeOrderIds;
            Specification<ProcurementMovement> movSpec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(cb.isFalse(root.get("isDeleted")));
                if (finalScopeIds != null) {
                    predicates.add(root.get("purchaseOrderId").in(finalScopeIds));
                }
                if (from != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
                }
                if (to != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
                }
                if (keyword != null && !keyword.isBlank()) {
                    String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                    Predicate textMatch = cb.or(
                            cb.like(cb.lower(cb.coalesce(root.<String>get("reference"), "")), pattern),
                            cb.like(cb.lower(cb.coalesce(root.<String>get("reason"), "")), pattern)
                    );
                    if (!keywordOrderIds.isEmpty()) {
                        predicates.add(cb.or(textMatch, root.get("purchaseOrderId").in(keywordOrderIds)));
                    } else {
                        predicates.add(textMatch);
                    }
                }
                return cb.and(predicates.toArray(Predicate[]::new));
            };
            matchedMovements = movements.findAll(movSpec);
        }

        long receiptCount = 0;
        BigDecimal totalReceiptAmount = BigDecimal.ZERO;
        long supplierReturnCount = 0;
        BigDecimal totalReturnAmount = BigDecimal.ZERO;
        BigDecimal totalPaymentAmount = BigDecimal.ZERO;
        BigDecimal totalRefundAmount = BigDecimal.ZERO;

        for (ProcurementMovement m : matchedMovements) {
            BigDecimal amount = m.getAmount() == null ? BigDecimal.ZERO : m.getAmount();
            switch (m.getKind() == null ? "" : m.getKind().toUpperCase(Locale.ROOT)) {
                case "RECEIPT" -> {
                    receiptCount++;
                    totalReceiptAmount = totalReceiptAmount.add(amount);
                }
                case "SUPPLIER_RETURN" -> {
                    supplierReturnCount++;
                    totalReturnAmount = totalReturnAmount.add(amount);
                }
                case "PAYMENT" -> totalPaymentAmount = totalPaymentAmount.add(amount);
                case "SUPPLIER_REFUND" -> totalRefundAmount = totalRefundAmount.add(amount);
                default -> {}
            }
        }

        Specification<PurchaseOrder> poSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            predicates.add(root.get("status").in(
                    PurchaseOrder.Status.PARTIALLY_RECEIVED,
                    PurchaseOrder.Status.RECEIVED
            ));
            if (supplierId != null) {
                predicates.add(cb.equal(root.get("supplier").get("id"), supplierId));
            }
            if (warehouseId != null) {
                predicates.add(cb.equal(root.get("warehouseId"), warehouseId));
            }
            var dateExpr = cb.coalesce(root.<Instant>get("receivedAt"), root.<Instant>get("createdAt"));
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(dateExpr, from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(dateExpr, to));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                var supplierJoin = root.join("supplier", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.<String>get("orderNumber"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(supplierJoin.<String>get("name"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(supplierJoin.<String>get("code"), "")), pattern)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        List<PurchaseOrder> receivedOrders = orders.findAll(poSpec);
        BigDecimal receivedOrdersValue = BigDecimal.ZERO;
        BigDecimal totalPoPaid = BigDecimal.ZERO;
        for (PurchaseOrder po : receivedOrders) {
            if (po.getItems() != null) {
                for (PurchaseOrderItem item : po.getItems()) {
                    int rec = item.getReceivedQuantity() == null ? 0 : item.getReceivedQuantity();
                    int ret = item.getReturnedQuantity() == null ? 0 : item.getReturnedQuantity();
                    int net = Math.max(0, rec - ret);
                    if (item.getUnitCost() != null && net > 0) {
                        receivedOrdersValue = receivedOrdersValue.add(
                                item.getUnitCost().multiply(BigDecimal.valueOf(net)));
                    }
                }
            }
            if (po.getPaidAmount() != null) {
                totalPoPaid = totalPoPaid.add(po.getPaidAmount());
            }
        }

        BigDecimal netPaidAmount = totalPaymentAmount.subtract(totalRefundAmount);
        BigDecimal outstandingPayable = receivedOrdersValue.subtract(totalPoPaid);

        return new ProcurementHistorySummary(
                receiptCount,
                totalReceiptAmount,
                supplierReturnCount,
                totalReturnAmount,
                totalPaymentAmount,
                totalRefundAmount,
                netPaidAmount,
                receivedOrders.size(),
                receivedOrdersValue,
                outstandingPayable
        );
    }

    private List<Long> findMatchingOrderIds(String keyword, Long supplierId, Long warehouseId) {
        Specification<PurchaseOrder> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (supplierId != null) {
                predicates.add(cb.equal(root.get("supplier").get("id"), supplierId));
            }
            if (warehouseId != null) {
                predicates.add(cb.equal(root.get("warehouseId"), warehouseId));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                var supplierJoin = root.join("supplier", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.<String>get("orderNumber"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(supplierJoin.<String>get("name"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(supplierJoin.<String>get("code"), "")), pattern)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return orders.findAll(spec).stream().map(PurchaseOrder::getId).toList();
    }

    private ProcurementMovementHistoryView toHistoryView(
            ProcurementMovement m,
            PurchaseOrder po,
            Map<Long, PurchaseOrderItem> itemMap,
            Map<Long, ProductVariantSummaryDto> variants) {
        List<StoredLine> rawLines = parseLines(m.getLinesJson());
        List<MovementLineView> lineViews = new ArrayList<>(rawLines.size());
        int totalQty = 0;
        for (StoredLine raw : rawLines) {
            int qty = raw.quantity() == null ? 0 : raw.quantity();
            totalQty += qty;
            PurchaseOrderItem item = raw.itemId() == null ? null : itemMap.get(raw.itemId());
            Long variantId = item != null ? item.getProductVariantId() : null;
            ProductVariantSummaryDto variant = variantId != null ? variants.get(variantId) : null;
            BigDecimal unitCost = item != null ? item.getUnitCost() : null;
            BigDecimal lineAmount = unitCost != null ? unitCost.multiply(BigDecimal.valueOf(qty)) : null;
            lineViews.add(new MovementLineView(
                    raw.itemId(),
                    variantId,
                    variant != null ? variant.getProductName() : null,
                    variant != null ? variant.getVariantName() : null,
                    variant != null ? variant.getSku() : null,
                    qty,
                    unitCost,
                    lineAmount,
                    raw.batchCode(),
                    raw.expirationDate()
            ));
        }

        return new ProcurementMovementHistoryView(
                m.getId(),
                m.getPurchaseOrderId(),
                po != null ? po.getOrderNumber() : ("PO #" + m.getPurchaseOrderId()),
                po != null ? po.getStatus() : null,
                po != null && po.getSupplier() != null ? po.getSupplier().getId() : null,
                po != null && po.getSupplier() != null ? po.getSupplier().getCode() : null,
                po != null && po.getSupplier() != null ? po.getSupplier().getName() : null,
                po != null ? po.getWarehouseId() : null,
                m.getKind(),
                m.getReference(),
                m.getAmount(),
                m.getReason(),
                m.getCreatedAt(),
                m.getCreatedBy(),
                totalQty,
                lineViews
        );
    }

    private List<StoredLine> parseLines(String linesJson) {
        if (linesJson == null || linesJson.isBlank() || "[]".equals(linesJson.trim())) {
            return List.of();
        }
        try {
            List<StoredLine> parsed = objectMapper.readValue(linesJson, new TypeReference<>() {});
            return parsed == null ? List.of() : parsed;
        } catch (Exception ignored) {
            return List.of();
        }
    }
}

