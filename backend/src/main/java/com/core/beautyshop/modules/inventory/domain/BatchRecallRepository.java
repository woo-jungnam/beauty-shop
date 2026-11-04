package com.core.beautyshop.modules.inventory.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BatchRecallRepository extends JpaRepository<BatchRecall, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BatchRecall r where r.id = :id")
    Optional<BatchRecall> lock(@Param("id") Long id);

    Optional<BatchRecall> findByReference(String reference);

    List<BatchRecall> findByVariantIdAndBatchCodeAndActiveTrue(Long variantId, String batchCode);
}
