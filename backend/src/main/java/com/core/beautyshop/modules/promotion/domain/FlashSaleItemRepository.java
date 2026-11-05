package com.core.beautyshop.modules.promotion.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FlashSaleItemRepository extends JpaRepository<FlashSaleItem, Long> {

    List<FlashSaleItem> findByCampaignIdOrderByDisplayOrderAsc(Long campaignId);

    @Query("SELECT i FROM FlashSaleItem i JOIN i.campaign c " +
            "WHERE i.productVariantId = :variantId " +
            "AND c.isDeleted = false " +
            "AND (c.status = 'ACTIVE' OR (c.status = 'SCHEDULED' AND c.startsAt <= :now AND c.endsAt > :now)) " +
            "AND (i.quantityQuota - i.quantitySold) > 0 " +
            "ORDER BY i.flashPrice ASC")
    List<FlashSaleItem> findActiveFlashItemsForVariant(@Param("variantId") Long variantId, @Param("now") Instant now);

    @Modifying
    @Query(value = "UPDATE flash_sale_items SET quantity_sold = quantity_sold + :qty " +
            "WHERE id = :id AND (quantity_quota - quantity_sold) >= :qty", nativeQuery = true)
    int reserveQuotaAtomic(@Param("id") Long id, @Param("qty") int qty);

    @Modifying
    @Query(value = "UPDATE flash_sale_items SET quantity_sold = GREATEST(0, quantity_sold - :qty) " +
            "WHERE id = :id", nativeQuery = true)
    int releaseQuotaAtomic(@Param("id") Long id, @Param("qty") int qty);
}
