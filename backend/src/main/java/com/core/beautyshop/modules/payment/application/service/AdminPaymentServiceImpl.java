package com.core.beautyshop.modules.payment.application.service;

import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.payment.api.AdminPaymentController;
import com.core.beautyshop.modules.payment.domain.PaymentResolutionRepository;
import com.core.beautyshop.modules.payment.domain.PaymentTransaction;
import com.core.beautyshop.modules.payment.domain.PaymentTransactionRepository;
import com.core.beautyshop.modules.payment.domain.enums.TransactionStatus;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AdminPaymentServiceImpl implements AdminPaymentService {
    private final PaymentTransactionRepository repository;
    private final OrderFacade orders;
    private final PaymentResolutionRepository resolutions;

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentTransaction> list(String keyword, TransactionStatus status, Pageable pageable) {
        Specification<PaymentTransaction> spec = Specification.where(null);
        if (keyword != null && !keyword.isBlank()) {
            String value = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("orderNumber")), value),
                    cb.like(cb.lower(root.get("referenceCode")), value),
                    cb.like(cb.lower(root.get("content")), value)));
        }
        if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        return repository.findAll(spec, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentTransaction getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment transaction not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminPaymentController.Summary summary(Instant from, Instant to) {
        if (from != null && to != null && !from.isBefore(to))
            throw new BusinessException("From must precede to");
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
        BigDecimal refunded = orders.getConfirmedRefundAmount(from, to).add(resolutions.refundAmount(from, to));
        return new AdminPaymentController.Summary(total, received, failed, matched, cancelled, unmatched, unidentified,
                refunded, cod, cash, received.subtract(cod).subtract(cash));
    }
}
