package com.core.beautyshop.modules.spa.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;

public interface FacilityBlockRepository extends JpaRepository<FacilityBlock, Long> {
    List<FacilityBlock> findByFacilityId(Long facilityId);
    List<FacilityBlock> findByFacilityIdAndIsDeletedFalseOrderByStartAtAsc(Long facilityId);
    @Query("select count(b) > 0 from FacilityBlock b where b.facility.id = :facilityId and b.isDeleted = false and b.startAt < :endAt and b.endAt > :startAt")
    boolean overlaps(Long facilityId, LocalDateTime startAt, LocalDateTime endAt);
}
