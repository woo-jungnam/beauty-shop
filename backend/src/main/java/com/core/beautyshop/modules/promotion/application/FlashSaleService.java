package com.core.beautyshop.modules.promotion.application;

import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import com.core.beautyshop.modules.inventory.api.InventoryFacade;
import com.core.beautyshop.modules.promotion.api.FlashSaleFacade;
import com.core.beautyshop.modules.promotion.domain.*;
import com.core.beautyshop.modules.promotion.domain.enums.FlashSaleStatus;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;

@Service
@RequiredArgsConstructor
public class FlashSaleService implements FlashSaleFacade {

    private final FlashSaleCampaignRepository campaignRepository;
    private final FlashSaleItemRepository itemRepository;
    private final FlashSaleOrderAllocationRepository allocationRepository;
    private final CatalogFacade catalogFacade;
    private final InventoryFacade inventoryFacade;
    private final JdbcTemplate jdbcTemplate;

    // =========================================================================
    // ADMIN API OPERATIONS
    // =========================================================================

    @Transactional(readOnly = true)
    public Page<CampaignView> findAll(Pageable pageable) {
        return campaignRepository.findAllActive(pageable).map(this::toCampaignView);
    }

    @Transactional(readOnly = true)
    public CampaignDetailView getById(Long id) {
        FlashSaleCampaign campaign = campaignRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chiến dịch Flash Sale: " + id));
        return toCampaignDetailView(campaign);
    }

    @Transactional
    public CampaignDetailView create(CampaignCommand command) {
        validateCommand(command);
        FlashSaleCampaign campaign = FlashSaleCampaign.builder()
                .name(command.name().trim())
                .description(command.description() != null ? command.description().trim() : null)
                .bannerUrl(command.bannerUrl() != null ? command.bannerUrl().trim() : null)
                .startsAt(command.startsAt())
                .endsAt(command.endsAt())
                .status(command.status() != null ? command.status() : FlashSaleStatus.SCHEDULED)
                .build();

        FlashSaleCampaign saved = campaignRepository.save(campaign);
        if (command.items() != null && !command.items().isEmpty()) {
            addItemsToCampaign(saved, command.items());
        }
        return getById(saved.getId());
    }

    @Transactional
    public CampaignDetailView update(Long id, CampaignCommand command) {
        validateCommand(command);
        FlashSaleCampaign campaign = campaignRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chiến dịch Flash Sale: " + id));

        campaign.setName(command.name().trim());
        campaign.setDescription(command.description() != null ? command.description().trim() : null);
        campaign.setBannerUrl(command.bannerUrl() != null ? command.bannerUrl().trim() : null);
        campaign.setStartsAt(command.startsAt());
        campaign.setEndsAt(command.endsAt());
        if (command.status() != null) {
            campaign.setStatus(command.status());
        }

        // Cập nhật lại danh sách items
        campaign.getItems().clear();
        campaignRepository.saveAndFlush(campaign);

        if (command.items() != null && !command.items().isEmpty()) {
            addItemsToCampaign(campaign, command.items());
        }
        return getById(id);
    }

    @Transactional
    public void delete(Long id) {
        FlashSaleCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chiến dịch Flash Sale: " + id));
        campaign.setIsDeleted(true);
        campaign.setStatus(FlashSaleStatus.ENDED);
        campaignRepository.save(campaign);
    }

    @Transactional(readOnly = true)
    public List<FlashSaleCandidateItem> getCandidateItems() {
        LocalDate today = com.core.beautyshop.shared.time.BusinessTime.today();
        String sql = """
            SELECT
                p.id AS product_id,
                p.name AS product_name,
                p.thumbnail_url AS product_thumbnail_url,
                pv.id AS variant_id,
                pv.sku AS sku,
                pv.variant_name AS variant_name,
                pv.price AS original_price,
                ws.id AS stock_id,
                ws.warehouse_id AS warehouse_id,
                w.name AS warehouse_name,
                ws.batch_code AS batch_code,
                ws.expiration_date AS expiration_date,
                ws.quantity AS quantity,
                ws.reserved_quantity AS reserved_quantity,
                ws.cost_price AS cost_price
            FROM product_variants pv
            JOIN products p ON p.id = pv.product_id
            LEFT JOIN warehouse_stocks ws ON ws.product_variant_id = pv.id
                AND ws.is_deleted = false
                AND ws.is_blocked = false
            LEFT JOIN warehouses w ON w.id = ws.warehouse_id
                AND w.is_deleted = false
                AND w.is_active = true
            WHERE pv.is_deleted = false
              AND pv.is_active = true
              AND p.is_deleted = false
            ORDER BY p.name ASC, pv.sku ASC, ws.expiration_date ASC
            """;

        Map<Long, List<Map<String, Object>>> rowsByVariant = new LinkedHashMap<>();
        Map<Long, Map<String, Object>> variantInfoMap = new LinkedHashMap<>();

        jdbcTemplate.query(sql, rs -> {
            Long variantId = rs.getLong("variant_id");
            if (!variantInfoMap.containsKey(variantId)) {
                Map<String, Object> vInfo = new HashMap<>();
                vInfo.put("productId", rs.getLong("product_id"));
                vInfo.put("productName", rs.getString("product_name"));
                vInfo.put("productThumbnailUrl", rs.getString("product_thumbnail_url"));
                vInfo.put("variantId", variantId);
                vInfo.put("sku", rs.getString("sku"));
                vInfo.put("variantName", rs.getString("variant_name"));
                vInfo.put("originalPrice", rs.getBigDecimal("original_price"));
                variantInfoMap.put(variantId, vInfo);
                rowsByVariant.put(variantId, new ArrayList<>());
            }

            long stockId = rs.getLong("stock_id");
            if (!rs.wasNull()) {
                Map<String, Object> bInfo = new HashMap<>();
                bInfo.put("stockId", stockId);
                bInfo.put("warehouseId", rs.getLong("warehouse_id"));
                bInfo.put("warehouseName", rs.getString("warehouse_name"));
                bInfo.put("batchCode", rs.getString("batch_code"));
                java.sql.Date expSql = rs.getDate("expiration_date");
                LocalDate expDate = expSql != null ? expSql.toLocalDate() : null;
                bInfo.put("expirationDate", expDate);
                int qty = rs.getInt("quantity");
                int reserved = rs.getInt("reserved_quantity");
                bInfo.put("quantity", qty);
                bInfo.put("reservedQuantity", reserved);
                bInfo.put("availableQuantity", Math.max(0, qty - reserved));
                bInfo.put("costPrice", rs.getBigDecimal("cost_price"));
                rowsByVariant.get(variantId).add(bInfo);
            }
        });

        List<FlashSaleCandidateItem> results = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Object>> entry : variantInfoMap.entrySet()) {
            Long vId = entry.getKey();
            Map<String, Object> vInfo = entry.getValue();
            List<Map<String, Object>> bList = rowsByVariant.getOrDefault(vId, List.of());

            List<StockBatchView> batchViews = new ArrayList<>();
            int totalAvailable = 0;

            for (Map<String, Object> b : bList) {
                int avail = (int) b.get("availableQuantity");
                totalAvailable += avail;
                LocalDate exp = (LocalDate) b.get("expirationDate");
                Integer daysRemaining = null;
                boolean isExpiringSoon = false;
                if (exp != null) {
                    daysRemaining = (int) ChronoUnit.DAYS.between(today, exp);
                    if (daysRemaining <= 90 && daysRemaining > 0) {
                        isExpiringSoon = true;
                    }
                }

                batchViews.add(new StockBatchView(
                        (Long) b.get("stockId"),
                        (Long) b.get("warehouseId"),
                        (String) b.get("warehouseName"),
                        (String) b.get("batchCode"),
                        exp,
                        daysRemaining,
                        isExpiringSoon,
                        (int) b.get("quantity"),
                        (int) b.get("reservedQuantity"),
                        avail,
                        (BigDecimal) b.get("costPrice")
                ));
            }

            results.add(new FlashSaleCandidateItem(
                    (Long) vInfo.get("productId"),
                    (String) vInfo.get("productName"),
                    (String) vInfo.get("productThumbnailUrl"),
                    vId,
                    (String) vInfo.get("sku"),
                    (String) vInfo.get("variantName"),
                    (BigDecimal) vInfo.get("originalPrice"),
                    totalAvailable,
                    batchViews
            ));
        }

        return results;
    }

    private void addItemsToCampaign(FlashSaleCampaign campaign, List<ItemCommand> items) {
        List<Long> variantIds = items.stream().map(ItemCommand::productVariantId).toList();
        Map<Long, ProductVariantSummaryDto> variants = catalogFacade.getVariantSummariesByIds(variantIds);

        int order = 0;
        for (ItemCommand it : items) {
            ProductVariantSummaryDto v = variants.get(it.productVariantId());
            if (v == null) {
                throw new BusinessException("Biến thể SKU không tồn tại: " + it.productVariantId());
            }
            if (it.flashPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("Giá Flash Sale phải lớn hơn 0");
            }
            if (it.flashPrice().compareTo(v.getPrice()) >= 0) {
                throw new BusinessException("Giá Flash Sale (" + it.flashPrice() + ") phải thấp hơn giá gốc niêm yết (" + v.getPrice() + ") của " + v.getVariantName());
            }
            if (it.quantityQuota() <= 0) {
                throw new BusinessException("Số lượng suất bán Flash Sale phải lớn hơn 0");
            }

            FlashSaleItem item = FlashSaleItem.builder()
                    .campaign(campaign)
                    .productId(v.getProductId())
                    .productVariantId(v.getId())
                    .flashPrice(it.flashPrice())
                    .quantityQuota(it.quantityQuota())
                    .quantitySold(0)
                    .userLimit(it.userLimit() != null && it.userLimit() > 0 ? it.userLimit() : 2)
                    .displayOrder(it.displayOrder() != null ? it.displayOrder() : order++)
                    .build();
            campaign.getItems().add(item);
        }
        campaignRepository.save(campaign);
    }

    private void validateCommand(CampaignCommand command) {
        if (command.startsAt() == null || command.endsAt() == null) {
            throw new BusinessException("Thời gian bắt đầu và kết thúc chiến dịch là bắt buộc");
        }
        if (!command.endsAt().isAfter(command.startsAt())) {
            throw new BusinessException("Thời gian kết thúc phải diễn ra sau thời gian bắt đầu");
        }
    }

    // =========================================================================
    // PUBLIC CUSTOMER API OPERATIONS
    // =========================================================================

    @Transactional(readOnly = true)
    public CustomerFlashSaleOverview getCustomerFlashSaleOverview() {
        Instant now = Instant.now();
        List<FlashSaleCampaign> running = campaignRepository.findRunningCampaigns(now);
        List<FlashSaleCampaign> upcoming = campaignRepository.findUpcomingCampaigns(now, now.plus(Duration.ofHours(24)));

        FlashSaleCampaign currentSlot = running.isEmpty() ? null : running.getFirst();
        List<CustomerSlotItem> slotItems = new ArrayList<>();

        if (currentSlot != null) {
            List<FlashSaleItem> items = itemRepository.findByCampaignIdOrderByDisplayOrderAsc(currentSlot.getId());
            slotItems = enrichSlotItems(items);
        }

        List<CustomerSlotHeader> headers = new ArrayList<>();
        if (currentSlot != null) {
            headers.add(new CustomerSlotHeader(currentSlot.getId(), currentSlot.getName(), currentSlot.getStartsAt(), currentSlot.getEndsAt(), true, "Đang diễn ra"));
        }
        for (FlashSaleCampaign up : upcoming) {
            if (currentSlot == null || !up.getId().equals(currentSlot.getId())) {
                headers.add(new CustomerSlotHeader(up.getId(), up.getName(), up.getStartsAt(), up.getEndsAt(), false, "Sắp mở bán"));
            }
        }

        return new CustomerFlashSaleOverview(
                currentSlot != null ? currentSlot.getId() : null,
                currentSlot != null ? currentSlot.getName() : null,
                currentSlot != null ? currentSlot.getStartsAt() : null,
                currentSlot != null ? currentSlot.getEndsAt() : null,
                headers,
                slotItems
        );
    }

    @Transactional(readOnly = true)
    public List<CustomerSlotItem> getCampaignItems(Long campaignId) {
        List<FlashSaleItem> items = itemRepository.findByCampaignIdOrderByDisplayOrderAsc(campaignId);
        return enrichSlotItems(items);
    }

    private List<CustomerSlotItem> enrichSlotItems(List<FlashSaleItem> items) {
        if (items.isEmpty()) return List.of();
        List<Long> variantIds = items.stream().map(FlashSaleItem::getProductVariantId).toList();
        Map<Long, ProductVariantSummaryDto> variants = catalogFacade.getVariantSummariesByIds(variantIds);
        Map<Long, Integer> stockMap = inventoryFacade.getAvailableQuantities(variantIds);

        return items.stream().map(it -> {
            ProductVariantSummaryDto v = variants.get(it.getProductVariantId());
            int quotaRemaining = Math.max(0, it.getQuantityQuota() - it.getQuantitySold());
            int realStock = stockMap.getOrDefault(it.getProductVariantId(), 0);
            int remaining = Math.min(quotaRemaining, realStock);
            int soldPercent = it.getQuantityQuota() > 0
                    ? Math.min(100, (int) Math.round(((double) it.getQuantitySold() / it.getQuantityQuota()) * 100))
                    : 0;

            return new CustomerSlotItem(
                    it.getId(),
                    it.getProductId(),
                    it.getProductVariantId(),
                    v != null ? v.getProductName() : ("Sản phẩm #" + it.getProductId()),
                    v != null ? v.getVariantName() : null,
                    v != null ? v.getSku() : null,
                    v != null ? v.getProductThumbnailUrl() : null,
                    v != null ? v.getPrice() : it.getFlashPrice(),
                    it.getFlashPrice(),
                    it.getQuantityQuota(),
                    it.getQuantitySold(),
                    remaining,
                    soldPercent,
                    it.getUserLimit()
            );
        }).toList();
    }

    // =========================================================================
    // FACADE IMPLEMENTATION (PRICING RESOLUTION & ORDER ALLOCATION)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public Optional<FlashSalePriceResolution> resolveActiveFlashSale(Long variantId) {
        return resolveActiveFlashSale(variantId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FlashSalePriceResolution> resolveActiveFlashSale(Long variantId, Long userId) {
        if (variantId == null) return Optional.empty();
        List<FlashSaleItem> activeItems = itemRepository.findActiveFlashItemsForVariant(variantId, Instant.now());
        if (activeItems.isEmpty()) return Optional.empty();

        FlashSaleItem item = activeItems.getFirst();
        int remainingQuota = item.getQuantityQuota() - item.getQuantitySold();
        if (remainingQuota <= 0) return Optional.empty();

        int userLimit = item.getUserLimit() != null && item.getUserLimit() > 0 ? item.getUserLimit() : Integer.MAX_VALUE;
        int alreadyBought = (userId != null && item.getUserLimit() != null && item.getUserLimit() > 0)
                ? allocationRepository.countUserPurchasedQuantity(item.getId(), userId)
                : 0;

        int userRemaining = Math.max(0, userLimit - alreadyBought);
        if (userRemaining <= 0) return Optional.empty();

        int remainingUserQuota = Math.min(remainingQuota, userRemaining);

        return Optional.of(new FlashSalePriceResolution(
                item.getId(),
                item.getFlashPrice(),
                true,
                remainingQuota,
                remainingUserQuota
        ));
    }

    @Override
    @Transactional
    public Optional<FlashSaleAllocation> tryAllocateFlashSale(Long variantId, int requestedQuantity, Long userId) {
        if (variantId == null || requestedQuantity <= 0) return Optional.empty();
        Instant now = Instant.now();
        List<FlashSaleItem> activeItems = itemRepository.findActiveFlashItemsForVariant(variantId, now);
        if (activeItems.isEmpty()) return Optional.empty();

        FlashSaleItem item = activeItems.getFirst();

        int userLimit = item.getUserLimit() != null && item.getUserLimit() > 0 ? item.getUserLimit() : Integer.MAX_VALUE;
        int alreadyBought = (userId != null && item.getUserLimit() != null && item.getUserLimit() > 0)
                ? allocationRepository.countUserPurchasedQuantity(item.getId(), userId)
                : 0;

        int userRemaining = Math.max(0, userLimit - alreadyBought);
        if (userRemaining <= 0) return Optional.empty();

        int remainingQuota = Math.max(0, item.getQuantityQuota() - item.getQuantitySold());
        if (remainingQuota <= 0) return Optional.empty();

        int allocQty = Math.min(requestedQuantity, Math.min(userRemaining, remainingQuota));
        if (allocQty <= 0) return Optional.empty();

        int updatedRows = itemRepository.reserveQuotaAtomic(item.getId(), allocQty);
        if (updatedRows > 0) {
            return Optional.of(new FlashSaleAllocation(
                    item.getId(),
                    item.getFlashPrice(),
                    allocQty,
                    userId
            ));
        }

        return Optional.empty();
    }

    @Override
    @Transactional
    public void confirmAllocation(Long orderId, Long flashSaleItemId, Long userId, int quantity) {
        if (orderId == null || flashSaleItemId == null || quantity <= 0) return;
        FlashSaleOrderAllocation allocation = FlashSaleOrderAllocation.builder()
                .flashSaleItemId(flashSaleItemId)
                .orderId(orderId)
                .userId(userId != null ? userId : 0L)
                .quantity(quantity)
                .allocatedAt(Instant.now())
                .build();
        allocationRepository.save(allocation);
    }

    @Override
    @Transactional
    public void releaseAllocations(Long orderId) {
        if (orderId == null) return;
        List<FlashSaleOrderAllocation> allocations = allocationRepository.findByOrderIdAndReleasedAtIsNull(orderId);
        Instant now = Instant.now();
        for (FlashSaleOrderAllocation alloc : allocations) {
            itemRepository.releaseQuotaAtomic(alloc.getFlashSaleItemId(), alloc.getQuantity());
            alloc.setReleasedAt(now);
        }
        allocationRepository.saveAll(allocations);
    }

    // =========================================================================
    // MAPPER HELPERS
    // =========================================================================

    private CampaignView toCampaignView(FlashSaleCampaign c) {
        int totalItems = c.getItems() != null ? c.getItems().size() : 0;
        int totalQuota = c.getItems() != null ? c.getItems().stream().mapToInt(FlashSaleItem::getQuantityQuota).sum() : 0;
        int totalSold = c.getItems() != null ? c.getItems().stream().mapToInt(FlashSaleItem::getQuantitySold).sum() : 0;

        return new CampaignView(
                c.getId(),
                c.getName(),
                c.getDescription(),
                c.getBannerUrl(),
                c.getStartsAt(),
                c.getEndsAt(),
                c.getStatus(),
                totalItems,
                totalQuota,
                totalSold
        );
    }

    private CampaignDetailView toCampaignDetailView(FlashSaleCampaign c) {
        List<CustomerSlotItem> enrichedItems = enrichSlotItems(c.getItems() != null ? c.getItems() : List.of());
        int totalQuota = enrichedItems.stream().mapToInt(CustomerSlotItem::quantityQuota).sum();
        int totalSold = enrichedItems.stream().mapToInt(CustomerSlotItem::quantitySold).sum();

        return new CampaignDetailView(
                c.getId(),
                c.getName(),
                c.getDescription(),
                c.getBannerUrl(),
                c.getStartsAt(),
                c.getEndsAt(),
                c.getStatus(),
                enrichedItems.size(),
                totalQuota,
                totalSold,
                enrichedItems
        );
    }

    // =========================================================================
    // RECORD COMMANDS & VIEWS
    // =========================================================================

    public record CampaignCommand(
            @NotBlank String name,
            String description,
            String bannerUrl,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            FlashSaleStatus status,
            List<ItemCommand> items
    ) {}

    public record ItemCommand(
            @NotNull Long productVariantId,
            @NotNull @Positive BigDecimal flashPrice,
            @NotNull @Positive Integer quantityQuota,
            Integer userLimit,
            Integer displayOrder
    ) {}

    public record CampaignView(
            Long id,
            String name,
            String description,
            String bannerUrl,
            Instant startsAt,
            Instant endsAt,
            FlashSaleStatus status,
            int totalItems,
            int totalQuota,
            int totalSold
    ) {}

    public record CampaignDetailView(
            Long id,
            String name,
            String description,
            String bannerUrl,
            Instant startsAt,
            Instant endsAt,
            FlashSaleStatus status,
            int totalItems,
            int totalQuota,
            int totalSold,
            List<CustomerSlotItem> items
    ) {}

    public record CustomerFlashSaleOverview(
            Long currentSlotId,
            String currentSlotName,
            Instant startsAt,
            Instant endsAt,
            List<CustomerSlotHeader> slots,
            List<CustomerSlotItem> items
    ) {}

    public record CustomerSlotHeader(
            Long campaignId,
            String slotName,
            Instant startsAt,
            Instant endsAt,
            boolean isCurrent,
            String statusLabel
    ) {}

    public record CustomerSlotItem(
            Long flashSaleItemId,
            Long productId,
            Long productVariantId,
            String productName,
            String variantName,
            String sku,
            String thumbnailUrl,
            BigDecimal originalPrice,
            BigDecimal flashPrice,
            int quantityQuota,
            int quantitySold,
            int remainingQuota,
            int soldPercent,
            int userLimit
    ) {}

    public record FlashSaleCandidateItem(
            Long productId,
            String productName,
            String productThumbnailUrl,
            Long productVariantId,
            String sku,
            String variantName,
            BigDecimal originalPrice,
            int totalAvailableStock,
            List<StockBatchView> batches
    ) {}

    public record StockBatchView(
            Long stockId,
            Long warehouseId,
            String warehouseName,
            String batchCode,
            LocalDate expirationDate,
            Integer daysRemaining,
            boolean isExpiringSoon,
            int quantity,
            int reservedQuantity,
            int availableQuantity,
            BigDecimal costPrice
    ) {}
}
