package com.core.beautyshop.modules.promotion.application;

import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
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
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FlashSaleService implements FlashSaleFacade {

    private final FlashSaleCampaignRepository campaignRepository;
    private final FlashSaleItemRepository itemRepository;
    private final FlashSaleOrderAllocationRepository allocationRepository;
    private final CatalogFacade catalogFacade;

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

        return items.stream().map(it -> {
            ProductVariantSummaryDto v = variants.get(it.getProductVariantId());
            int remaining = Math.max(0, it.getQuantityQuota() - it.getQuantitySold());
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
        if (variantId == null) return Optional.empty();
        List<FlashSaleItem> activeItems = itemRepository.findActiveFlashItemsForVariant(variantId, Instant.now());
        if (activeItems.isEmpty()) return Optional.empty();

        FlashSaleItem item = activeItems.getFirst();
        int remaining = item.getQuantityQuota() - item.getQuantitySold();
        if (remaining <= 0) return Optional.empty();

        return Optional.of(new FlashSalePriceResolution(
                item.getId(),
                item.getFlashPrice(),
                true,
                remaining
        ));
    }

    @Override
    @Transactional
    public Optional<BigDecimal> tryAllocateFlashSale(Long variantId, int quantity, Long orderId, Long userId) {
        if (variantId == null || quantity <= 0) return Optional.empty();
        Instant now = Instant.now();
        List<FlashSaleItem> activeItems = itemRepository.findActiveFlashItemsForVariant(variantId, now);
        if (activeItems.isEmpty()) return Optional.empty();

        FlashSaleItem item = activeItems.getFirst();

        // Kiểm tra giới hạn mua mỗi tài khoản nếu có userId
        if (userId != null && item.getUserLimit() != null && item.getUserLimit() > 0) {
            int alreadyBought = allocationRepository.countUserPurchasedQuantity(item.getId(), userId);
            if (alreadyBought + quantity > item.getUserLimit()) {
                // Vượt quota user -> Không áp dụng giá Flash Sale cho đơn này
                return Optional.empty();
            }
        }

        // Cố gắng trừ quota nguyên tử (atomic decrement)
        int updatedRows = itemRepository.reserveQuotaAtomic(item.getId(), quantity);
        if (updatedRows > 0) {
            // Lưu bản ghi giữ chỗ
            if (orderId != null) {
                FlashSaleOrderAllocation allocation = FlashSaleOrderAllocation.builder()
                        .flashSaleItemId(item.getId())
                        .orderId(orderId)
                        .userId(userId != null ? userId : 0L)
                        .quantity(quantity)
                        .allocatedAt(now)
                        .build();
                allocationRepository.save(allocation);
            }
            return Optional.of(item.getFlashPrice());
        }

        return Optional.empty();
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
}
