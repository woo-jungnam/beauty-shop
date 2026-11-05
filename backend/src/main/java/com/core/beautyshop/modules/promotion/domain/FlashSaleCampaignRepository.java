package com.core.beautyshop.modules.promotion.domain;

import com.core.beautyshop.modules.promotion.domain.enums.FlashSaleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FlashSaleCampaignRepository extends JpaRepository<FlashSaleCampaign, Long> {

    @Query("SELECT c FROM FlashSaleCampaign c WHERE c.isDeleted = false ORDER BY c.startsAt DESC")
    Page<FlashSaleCampaign> findAllActive(Pageable pageable);

    @Query("SELECT c FROM FlashSaleCampaign c WHERE c.isDeleted = false " +
            "AND (c.status = 'ACTIVE' OR (c.status = 'SCHEDULED' AND c.startsAt <= :now AND c.endsAt > :now)) " +
            "ORDER BY c.startsAt ASC")
    List<FlashSaleCampaign> findRunningCampaigns(@Param("now") Instant now);

    @Query("SELECT c FROM FlashSaleCampaign c WHERE c.isDeleted = false " +
            "AND c.startsAt > :now AND c.startsAt <= :upcomingThreshold " +
            "AND c.status IN ('SCHEDULED', 'ACTIVE') ORDER BY c.startsAt ASC")
    List<FlashSaleCampaign> findUpcomingCampaigns(@Param("now") Instant now, @Param("upcomingThreshold") Instant upcomingThreshold);

    @Query("SELECT c FROM FlashSaleCampaign c LEFT JOIN FETCH c.items WHERE c.id = :id AND c.isDeleted = false")
    Optional<FlashSaleCampaign> findByIdWithItems(@Param("id") Long id);
}
