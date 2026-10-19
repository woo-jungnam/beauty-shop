package com.core.beautyshop.modules.promotion.application;

import com.core.beautyshop.modules.promotion.api.PromotionFacade;
import com.core.beautyshop.modules.promotion.domain.*;
import com.core.beautyshop.modules.promotion.domain.enums.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
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
        if (command.endsAt().isBefore(Instant.now())) {
            throw new BusinessException("Thời gian kết thúc khuyến mãi phải ở trong tương lai");
        }
        String code = normalize(command.code());
        if (voucherRepository.existsByCodeIgnoreCase(code)) throw new BusinessException("Mã khuyến mãi đã tồn tại");
        Voucher voucher = new Voucher();
        apply(voucher, command, code);
        return VoucherView.from(voucherRepository.save(voucher));
    }

    @Transactional
    public VoucherView update(Long id, VoucherCommand command) {
        validateCommand(command);
        Voucher voucher = voucherRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mã khuyến mãi: " + id));
        if (command.usageLimit() != null && command.usageLimit() < voucher.getUsedCount()) {
            throw new BusinessException("Giới hạn sử dụng không thể nhỏ hơn số lượt đã dùng");
        }
        String code = normalize(command.code());
        if (!voucher.getCode().equalsIgnoreCase(code) && voucherRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException("Mã khuyến mãi đã tồn tại");
        }
        apply(voucher, command, code);
        return VoucherView.from(voucher);
    }

    @Transactional
    public void delete(Long id) {
        Voucher voucher = voucherRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mã khuyến mãi: " + id));
        voucher.setIsDeleted(true);
        voucher.setIsActive(false);
    }

    @Override
    @Transactional
    public AppliedVoucher validate(String code, Long userId, BigDecimal orderAmount) {
        if (code == null || code.isBlank()) return null;
        if (userId == null) throw new BusinessException("Vui lòng đăng nhập để sử dụng mã khuyến mãi");
        Voucher voucher = voucherRepository.findByCodeForUpdate(normalize(code))
                .orElseThrow(() -> new BusinessException("Mã khuyến mãi không hợp lệ"));
        Instant now = Instant.now();
        if (!Boolean.TRUE.equals(voucher.getIsActive()) || now.isBefore(voucher.getStartsAt()) || !now.isBefore(voucher.getEndsAt())) {
            throw new BusinessException("Mã khuyến mãi chưa kích hoạt hoặc đã hết hạn");
        }
        if (voucher.getUsageLimit() != null && voucher.getUsedCount() >= voucher.getUsageLimit()) {
            throw new BusinessException("Mã khuyến mãi đã hết lượt sử dụng");
        }
        if (usageRepository.countByVoucherIdAndUserIdAndIsDeletedFalse(voucher.getId(), userId) >= voucher.getPerUserLimit()) {
            throw new BusinessException("Bạn đã dùng hết số lượt cho phép của mã khuyến mãi này");
        }
        if (orderAmount.compareTo(voucher.getMinOrderAmount()) < 0) {
            throw new BusinessException("Đơn hàng chưa đạt giá trị tối thiểu để áp dụng mã khuyến mãi");
        }
        BigDecimal discount = voucher.getDiscountType() == DiscountType.PERCENTAGE
                ? orderAmount.multiply(voucher.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN)
                : voucher.getDiscountValue();
        if (voucher.getMaxDiscountAmount() != null) discount = discount.min(voucher.getMaxDiscountAmount());
        return new AppliedVoucher(voucher.getId(), voucher.getCode(), discount.min(orderAmount).max(BigDecimal.ZERO));
    }

    @Transactional(readOnly = true)
    public VoucherValidationResponse validateForCustomer(String code, Long userId, BigDecimal orderAmount) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("Vui lòng nhập mã giảm giá");
        }
        if (userId == null) {
            throw new BusinessException("Vui lòng đăng nhập tài khoản để áp dụng mã giảm giá");
        }
        if (orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Đơn hàng chưa có giá trị để áp dụng mã giảm giá");
        }
        Voucher voucher = voucherRepository.findByCodeAndIsDeletedFalse(normalize(code))
                .orElseThrow(() -> new BusinessException("Mã giảm giá không tồn tại hoặc không hợp lệ"));
        Instant now = Instant.now();
        if (!Boolean.TRUE.equals(voucher.getIsActive()) || now.isBefore(voucher.getStartsAt()) || !now.isBefore(voucher.getEndsAt())) {
            throw new BusinessException("Mã giảm giá chưa kích hoạt hoặc đã hết hạn sử dụng");
        }
        if (voucher.getUsageLimit() != null && voucher.getUsedCount() >= voucher.getUsageLimit()) {
            throw new BusinessException("Mã giảm giá đã hết lượt sử dụng");
        }
        if (usageRepository.countByVoucherIdAndUserIdAndIsDeletedFalse(voucher.getId(), userId) >= voucher.getPerUserLimit()) {
            throw new BusinessException("Bạn đã dùng hết số lượt cho phép của mã giảm giá này");
        }
        if (orderAmount.compareTo(voucher.getMinOrderAmount()) < 0) {
            throw new BusinessException("Đơn hàng chưa đạt giá trị tối thiểu "
                    + java.text.NumberFormat.getIntegerInstance().format(voucher.getMinOrderAmount())
                    + " ₫ để áp dụng mã này");
        }
        BigDecimal discount = voucher.getDiscountType() == DiscountType.PERCENTAGE
                ? orderAmount.multiply(voucher.getDiscountValue()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                : voucher.getDiscountValue();
        if (voucher.getMaxDiscountAmount() != null) {
            discount = discount.min(voucher.getMaxDiscountAmount());
        }
        BigDecimal finalDiscount = discount.min(orderAmount).max(BigDecimal.ZERO);
        String message = voucher.getDiscountType() == DiscountType.PERCENTAGE
                ? "Áp dụng giảm " + voucher.getDiscountValue() + "% thành công"
                : "Áp dụng giảm " + java.text.NumberFormat.getIntegerInstance().format(finalDiscount) + " ₫ thành công";
        return new VoucherValidationResponse(
                voucher.getId(),
                voucher.getCode(),
                voucher.getName(),
                finalDiscount,
                voucher.getDiscountType(),
                voucher.getDiscountValue(),
                voucher.getMinOrderAmount(),
                voucher.getMaxDiscountAmount(),
                message
        );
    }

    @Override
    @Transactional
    public void redeem(AppliedVoucher applied, Long userId, Long orderId) {
        if (applied == null) return;
        if (usageRepository.existsByOrderIdAndIsDeletedFalse(orderId)) return;
        Voucher voucher = voucherRepository.findByCodeForUpdate(applied.code())
                .orElseThrow(() -> new BusinessException("Mã khuyến mãi không còn tồn tại"));
        if (voucher.getUsageLimit() != null && voucher.getUsedCount() >= voucher.getUsageLimit()) {
            throw new BusinessException("Mã khuyến mãi đã hết lượt sử dụng");
        }
        voucher.setUsedCount(Math.addExact(voucher.getUsedCount(), 1));
        usageRepository.save(VoucherUsage.builder().voucherId(voucher.getId()).userId(userId).orderId(orderId)
                .discountAmount(applied.discountAmount()).usedAt(Instant.now()).build());
    }

    @Override
    @Transactional
    public void releaseVoucher(Long orderId) {
        if (orderId == null) return;
        usageRepository.findByOrderIdAndIsDeletedFalse(orderId).ifPresent(usage -> {
            usage.setIsDeleted(true);
            usageRepository.save(usage);
            voucherRepository.findByIdForUpdate(usage.getVoucherId()).ifPresent(voucher -> {
                if (voucher.getUsedCount() > 0) {
                    voucher.setUsedCount(voucher.getUsedCount() - 1);
                    voucherRepository.save(voucher);
                }
            });
        });
    }

    private Voucher find(Long id) {
        return voucherRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mã khuyến mãi: " + id));
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
            throw new BusinessException("Mã voucher, tên và loại giảm giá là bắt buộc");
        }
        if (command.endsAt() == null || command.startsAt() == null || !command.endsAt().isAfter(command.startsAt())) {
            throw new BusinessException("Thời gian kết thúc khuyến mãi phải sau thời gian bắt đầu");
        }
        if (command.code().trim().length() > 50 || command.name().trim().length() > 150
                || command.description() != null && command.description().length() > 500) {
            throw new BusinessException("Văn bản mô tả voucher vượt quá độ dài tối đa");
        }
        if (command.minOrderAmount() != null && command.minOrderAmount().signum() < 0
                || command.maxDiscountAmount() != null && command.maxDiscountAmount().signum() < 0) {
            throw new BusinessException("Giá trị đơn tối thiểu và mức giảm tối đa không được âm");
        }
        if (command.discountValue() == null || command.discountValue().signum() <= 0) throw new BusinessException("Mức giảm giá phải lớn hơn 0");
        if (command.discountType() == DiscountType.PERCENTAGE && command.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException("Tỷ lệ phần trăm giảm giá không được vượt quá 100%");
        }
        if (command.usageLimit() != null && command.usageLimit() <= 0 || command.perUserLimit() != null && command.perUserLimit() <= 0) {
            throw new BusinessException("Giới hạn lượt dùng voucher phải lớn hơn 0");
        }
    }

    private String normalize(String code) { return code.trim().toUpperCase(java.util.Locale.ROOT); }

    @Schema(description = "Tạo/thay cấu hình voucher; validation nghiệp vụ tại service. Dùng voucher qua checkout đăng nhập, không có API apply công khai riêng")
    public record VoucherCommand(
            @Schema(description = "Không trống, trim/viết hoa, duy nhất", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 50, example = "WELCOME10") String code,
            @Schema(description = "Tên không trống, trim", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 150, example = "Ưu đãi khách mới") String name,
            @Schema(description = "Mô tả tùy chọn", maxLength = 500) String description,
            @Schema(description = "PERCENTAGE hoặc FIXED_AMOUNT", requiredMode = Schema.RequiredMode.REQUIRED) DiscountType discountType,
            @Schema(description = "Dương; PERCENTAGE≤100, FIXED_AMOUNT là VND", requiredMode = Schema.RequiredMode.REQUIRED, example = "10") BigDecimal discountValue,
            @Schema(description = "Trần giảm VND, không âm, tùy chọn", minimum = "0") BigDecimal maxDiscountAmount,
            @Schema(description = "Tổng hàng tối thiểu VND, không âm; mặc định 0", minimum = "0", defaultValue = "0") BigDecimal minOrderAmount,
            @Schema(description = "UTC Instant bắt đầu, gồm mốc", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-01T00:00:00Z") Instant startsAt,
            @Schema(description = "UTC Instant kết thúc, không gồm mốc; phải sau startsAt", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-11-01T00:00:00Z") Instant endsAt,
            @Schema(description = "Tổng lượt tối đa >0; null không giới hạn; update không thấp hơn usedCount", minimum = "1") Integer usageLimit,
            @Schema(description = "Lượt mỗi tài khoản >0, mặc định 1", minimum = "1", defaultValue = "1") Integer perUserLimit,
            @Schema(description = "Thiếu/null mặc định true", defaultValue = "true") Boolean active) { }

    @Schema(description = "Cấu hình và số lượt đã dùng; voucher soft-delete không xuất hiện trong list/detail")
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

    @Schema(description = "Kết quả kiểm tra mã voucher của khách hàng")
    public record VoucherValidationResponse(
            Long voucherId,
            String code,
            String name,
            BigDecimal discountAmount,
            DiscountType discountType,
            BigDecimal discountValue,
            BigDecimal minOrderAmount,
            BigDecimal maxDiscountAmount,
            String message) { }
}
