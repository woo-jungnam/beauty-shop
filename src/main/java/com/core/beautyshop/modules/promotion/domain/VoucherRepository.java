package com.core.beautyshop.modules.promotion.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    Page<Voucher> findByIsDeletedFalse(Pageable pageable);
    Optional<Voucher> findByIdAndIsDeletedFalse(Long id);
    boolean existsByCodeIgnoreCase(String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Voucher v where v.id = :id and v.isDeleted = false")
    Optional<Voucher> findByIdForUpdate(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Voucher v where upper(v.code) = upper(:code) and v.isDeleted = false")
    Optional<Voucher> findByCodeForUpdate(@Param("code") String code);
}
