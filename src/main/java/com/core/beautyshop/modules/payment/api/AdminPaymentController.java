package com.core.beautyshop.modules.payment.api;

import com.core.beautyshop.modules.payment.domain.PaymentTransaction;
import com.core.beautyshop.modules.payment.domain.PaymentTransactionRepository;
import com.core.beautyshop.modules.payment.domain.enums.TransactionStatus;
import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.dto.PageResponse;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Tag(name = "Đối soát & Giao dịch thanh toán (Admin)", description = "API quản trị giao dịch thanh toán trực tuyến, đối soát và báo cáo thực thu")
@RestController
@RequestMapping("/api/v1/admin/payments")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPaymentController {
    private final PaymentTransactionRepository repository;
    private final com.core.beautyshop.modules.order.api.OrderFacade orders;

    @Operation(summary = "Lấy sổ giao dịch thanh toán", description = "ADMIN; gồm SEPAY, COD và CASH. Status là kết quả đối soát giao dịch, khác PaymentStatus của order. Dữ liệu chi tiết có rawPayload dành cho đối soát quản trị.")
    @GetMapping
    public ApiResponse<PageResponse<PaymentTransaction>> list(@Parameter(description = "Tìm không phân biệt hoa thường trong orderNumber, referenceCode hoặc content") @RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) TransactionStatus status,
                                                               @ParameterObject Pageable pageable) {
        Specification<PaymentTransaction> spec = Specification.where(null);
        if (keyword != null && !keyword.isBlank()) {
            String value = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("orderNumber")), value),
                    cb.like(cb.lower(root.get("referenceCode")), value),
                    cb.like(cb.lower(root.get("content")), value)));
        }
        if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        return ApiResponse.success(PageResponse.of(repository.findAll(spec, pageable)));
    }

    @Operation(summary = "Xem chi tiết giao dịch thanh toán theo ID")
    @GetMapping("/{id}")
    public ApiResponse<PaymentTransaction> getById(@PathVariable Long id) {
        return ApiResponse.success(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment transaction not found: " + id)));
    }

    @Operation(summary = "Tổng hợp thực thu và hoàn tiền", description = "ADMIN; khoảng [from,to) theo createdAt của ledger, không theo ngày tạo đơn hoặc transactionDate payload. Bỏ một cận để không giới hạn cận đó; bỏ cả hai lấy toàn bộ. receivedAmount là tổng tiền vào dương ở SUCCESS/PARTIALLY_PAID/CANCELLED_ORDER_RECEIVED/ORDER_NOT_FOUND/IGNORED, không trừ hoàn; FAILED không tính tiền. refundedAmount là xác nhận đã hoàn thủ công theo thời điểm xác nhận trong cùng khoảng. COD/CASH/BANK tách riêng; bankAmount=receivedAmount−codAmount−cashAmount.")
    @GetMapping("/summary")
    public ApiResponse<Summary> summary(@Parameter(description = "Từ UTC Instant, gồm mốc", example = "2026-10-01T00:00:00Z") @RequestParam(required = false) java.time.Instant from,
                                        @Parameter(description = "Đến UTC Instant, không gồm mốc; phải sau from nếu có cả hai", example = "2026-10-02T00:00:00Z") @RequestParam(required = false) java.time.Instant to) {
        if (from != null && to != null && !from.isBefore(to))
            throw new com.core.beautyshop.shared.exception.BusinessException("From must precede to");
        var buckets = repository.summarize(from, to);
        long total = 0, failed = 0;
        BigDecimal matched = BigDecimal.ZERO, cancelled = BigDecimal.ZERO, unmatched = BigDecimal.ZERO, unidentified = BigDecimal.ZERO;
        BigDecimal cod = BigDecimal.ZERO, cash = BigDecimal.ZERO;
        for (var bucket : buckets) {
            total += bucket.getTransactionCount();
            BigDecimal amount = bucket.getReceivedAmount();
            if ("COD".equals(bucket.getGateway()) && bucket.getStatus() != TransactionStatus.FAILED) cod = cod.add(amount);
            if ("CASH".equals(bucket.getGateway()) && bucket.getStatus() != TransactionStatus.FAILED) cash = cash.add(amount);
            switch (bucket.getStatus()) {
                case SUCCESS, PARTIALLY_PAID -> matched = matched.add(amount);
                case CANCELLED_ORDER_RECEIVED -> cancelled = cancelled.add(amount);
                case ORDER_NOT_FOUND -> unmatched = unmatched.add(amount);
                case IGNORED -> unidentified = unidentified.add(amount);
                case FAILED -> failed += bucket.getTransactionCount();
            }
        }
        BigDecimal received = matched.add(cancelled).add(unmatched).add(unidentified);
        BigDecimal refunded = orders.getConfirmedRefundAmount(from, to);
        return ApiResponse.success(new Summary(total, received, failed, matched, cancelled, unmatched, unidentified,
                refunded, cod, cash, received.subtract(cod).subtract(cash)));
    }

    /** Incoming collections by recorded time, with COD and reception cash separate from bank receipts. */
    @Schema(description = "Tổng hợp ledger theo thời điểm ghi nhận; số tiền VND, tổng thực thu và số đã hoàn là hai chỉ tiêu riêng")
    public record Summary(@Schema(description = "Tất cả giao dịch trong khoảng, gồm cả out/FAILED/IGNORED") long totalTransactions,
                          @Schema(description = "Tiền vào dương được ghi nhận, không gồm FAILED và chưa trừ hoàn") BigDecimal receivedAmount,
                          @Schema(description = "Số giao dịch FAILED") long failedTransactions,
                          @Schema(description = "Tiền vào SUCCESS/PARTIALLY_PAID đã khớp đơn") BigDecimal matchedAmount,
                          @Schema(description = "Tiền vào đơn đã hủy/trả hoặc invoice đã hoàn") BigDecimal cancelledOrderAmount,
                          @Schema(description = "Có mã order nhưng không tìm thấy order") BigDecimal unmatchedOrderAmount,
                          @Schema(description = "Tiền vào không xác định mã order, status IGNORED") BigDecimal unidentifiedAmount,
                          @Schema(description = "Tiền ADMIN xác nhận đã hoàn trong khoảng, không phải lệnh chuyển tiền tự động") BigDecimal refundedAmount,
                          @Schema(description = "Thu COD khi xác nhận giao hàng") BigDecimal codAmount,
                          @Schema(description = "Thu CASH tại quầy cho invoice Spa") BigDecimal cashAmount,
                          @Schema(description = "Thực thu còn lại ngoài COD và CASH") BigDecimal bankAmount) { }
}
