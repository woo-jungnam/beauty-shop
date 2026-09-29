package com.core.beautyshop.modules.promotion.application;

import com.core.beautyshop.modules.promotion.domain.*;
import com.core.beautyshop.modules.promotion.domain.enums.DiscountType;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoucherServiceTest {
    @Mock VoucherRepository vouchers;
    @Mock VoucherUsageRepository usages;
    @InjectMocks VoucherService service;

    @Test
    void percentageDiscountHonorsMaximumAmount() {
        Voucher voucher = validVoucher();
        when(vouchers.findByCodeForUpdate("SAVE20")).thenReturn(Optional.of(voucher));
        when(usages.countByVoucherIdAndUserIdAndIsDeletedFalse(1L, 10L)).thenReturn(0L);

        var result = service.validate("save20", 10L, new BigDecimal("1000000"));

        assertEquals(new BigDecimal("50000"), result.discountAmount());
    }

    @Test
    void perUserLimitIsEnforced() {
        Voucher voucher = validVoucher();
        when(vouchers.findByCodeForUpdate("SAVE20")).thenReturn(Optional.of(voucher));
        when(usages.countByVoucherIdAndUserIdAndIsDeletedFalse(1L, 10L)).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.validate("SAVE20", 10L, new BigDecimal("1000000")));
    }

    private Voucher validVoucher() {
        Voucher voucher = Voucher.builder().code("SAVE20").discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("20")).maxDiscountAmount(new BigDecimal("50000"))
                .minOrderAmount(BigDecimal.ZERO).startsAt(Instant.now().minusSeconds(60))
                .endsAt(Instant.now().plusSeconds(3600)).perUserLimit(1).usedCount(0).isActive(true).build();
        voucher.setId(1L);
        return voucher;
    }
}
