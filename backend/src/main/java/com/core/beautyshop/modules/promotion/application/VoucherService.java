package com.core.beautyshop.modules.promotion.application;

import com.core.beautyshop.modules.promotion.api.PromotionFacade;
import com.core.beautyshop.modules.promotion.domain.*;
import com.core.beautyshop.modules.promotion.domain.enums.DiscountType;
import com.core.beautyshop.shared.exception.*;
import lombok.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class VoucherService implements PromotionFacade {
    private final VoucherRepository voucherRepository;
    private final VoucherUsageRepository usageRepository;

    @Transactional(readOnly = true)
    public Page<VoucherView> findAll(Pageable pageable) {
        return voucherRepository.findByIsDeletedFalse(pageable).map(VoucherView::from);
    }

    @Transactional(readOnly = true)
    public VoucherView get(Long id) {
        return VoucherView.from(find(id));
    }

    @Transactional
    public VoucherView create(VoucherCommand command) {
        validateCommand(command);
        String code = normalize(command.code());
        if (voucherRepository.existsByCodeIgnoreCase(code)) throw new BusinessException("Voucher code already exists");
        Voucher voucher = new Voucher();
        apply(voucher, command, code);
        return VoucherView.from(voucherRepository.save(voucher));
    }

    @Transactional
    public VoucherView update(Long id, VoucherCommand command) {
        validateCommand(command);
        Voucher voucher = find(id);
        String code = normalize(command.code());
        if (!voucher.getCode().equalsIgnoreCase(code) && voucherRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException("Voucher code already exists");
        }
        apply(voucher, command, code);
        return VoucherView.from(voucher);
    }

    @Transactional
    public void delete(Long id) {
        Voucher voucher = find(id);
        voucher.setIsDeleted(true);
        voucher.setIsActive(false);
    }

    @Override
    @Transactional
    public AppliedVoucher validate(String code, Long userId, BigDecimal orderAmount) {
        if (code == null || code.isBlank()) return null;
        if (userId == null) throw new BusinessException("Sign in to use a voucher");
        Voucher voucher = voucherRepository.findByCodeForUpdate(normalize(code))
                .orElseThrow(() -> new BusinessException("Voucher is invalid"));
        Instant now = Instant.now();
        if (!Boolean.TRUE.equals(voucher.getIsActive()) || now.isBefore(voucher.getStartsAt()) || !now.isBefore(voucher.getEndsAt())) {
            throw new BusinessException("Voucher is inactive or expired");
        }
        if (voucher.getUsageLimit() != null && voucher.getUsedCount() >= voucher.getUsageLimit()) {
            throw new BusinessException("Voucher usage limit has been reached");
        }
        if (usageRepository.countByVoucherIdAndUserIdAndIsDeletedFalse(voucher.getId(), userId) >= voucher.getPerUserLimit()) {
            throw new BusinessException("You have reached the usage limit for this voucher");
        }
        if (orderAmount.compareTo(voucher.getMinOrderAmount()) < 0) {
            throw new BusinessException("Order does not meet the voucher minimum amount");
        }
        BigDecimal discount = voucher.getDiscountType() == DiscountType.PERCENTAGE
                ? orderAmount.multiply(voucher.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN)
                : voucher.getDiscountValue();
        if (voucher.getMaxDiscountAmount() != null) discount = discount.min(voucher.getMaxDiscountAmount());
        return new AppliedVoucher(voucher.getId(), voucher.getCode(), discount.min(orderAmount).max(BigDecimal.ZERO));
    }

    @Override
    @Transactional
    public void redeem(AppliedVoucher applied, Long userId, Long orderId) {
        if (applied == null) return;
        if (usageRepository.existsByOrderId(orderId)) return;
        Voucher voucher = voucherRepository.findByCodeForUpdate(applied.code())
                .orElseThrow(() -> new BusinessException("Voucher no longer exists"));
        if (voucher.getUsageLimit() != null && voucher.getUsedCount() >= voucher.getUsageLimit()) {
            throw new BusinessException("Voucher usage limit has been reached");
        }
        voucher.setUsedCount(Math.addExact(voucher.getUsedCount(), 1));
        usageRepository.save(VoucherUsage.builder().voucherId(voucher.getId()).userId(userId).orderId(orderId)
                .discountAmount(applied.discountAmount()).usedAt(Instant.now()).build());
    }

    private Voucher find(Long id) {
        return voucherRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher not found: " + id));
    }

    private void apply(Voucher voucher, VoucherCommand command, String code) {
        voucher.setCode(code);
        voucher.setName(command.name().trim());
        voucher.setDescription(command.description());
        voucher.setDiscountType(command.discountType());
        voucher.setDiscountValue(command.discountValue());
        voucher.setMaxDiscountAmount(command.maxDiscountAmount());
        voucher.setMinOrderAmount(command.minOrderAmount() == null ? BigDecimal.ZERO : command.minOrderAmount());
        voucher.setStartsAt(command.startsAt());
        voucher.setEndsAt(command.endsAt());
        voucher.setUsageLimit(command.usageLimit());
        voucher.setPerUserLimit(command.perUserLimit() == null ? 1 : command.perUserLimit());
        voucher.setIsActive(command.active() == null || command.active());
    }

    private void validateCommand(VoucherCommand command) {
        if (command == null || command.code() == null || command.code().isBlank() || command.name() == null
                || command.name().isBlank() || command.discountType() == null) {
            throw new BusinessException("Voucher code, name and discount type are required");
        }
        if (command.endsAt() == null || command.startsAt() == null || !command.endsAt().isAfter(command.startsAt())) {
            throw new BusinessException("Voucher end time must be after start time");
        }
        if (command.discountValue() == null || command.discountValue().signum() <= 0) throw new BusinessException("Discount value must be positive");
        if (command.discountType() == DiscountType.PERCENTAGE && command.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException("Percentage discount cannot exceed 100");
        }
        if (command.usageLimit() != null && command.usageLimit() <= 0 || command.perUserLimit() != null && command.perUserLimit() <= 0) {
            throw new BusinessException("Voucher limits must be positive");
        }
    }

    private String normalize(String code) { return code.trim().toUpperCase(java.util.Locale.ROOT); }

    public record VoucherCommand(String code, String name, String description, DiscountType discountType,
            BigDecimal discountValue, BigDecimal maxDiscountAmount, BigDecimal minOrderAmount,
            Instant startsAt, Instant endsAt, Integer usageLimit, Integer perUserLimit, Boolean active) { }

    public record VoucherView(Long id, String code, String name, String description, DiscountType discountType,
            BigDecimal discountValue, BigDecimal maxDiscountAmount, BigDecimal minOrderAmount, Instant startsAt,
            Instant endsAt, Integer usageLimit, Integer perUserLimit, Integer usedCount, Boolean active) {
        static VoucherView from(Voucher voucher) {
            return new VoucherView(voucher.getId(), voucher.getCode(), voucher.getName(), voucher.getDescription(),
                    voucher.getDiscountType(), voucher.getDiscountValue(), voucher.getMaxDiscountAmount(),
                    voucher.getMinOrderAmount(), voucher.getStartsAt(), voucher.getEndsAt(), voucher.getUsageLimit(),
                    voucher.getPerUserLimit(), voucher.getUsedCount(), voucher.getIsActive());
        }
    }
}
