package com.core.beautyshop.modules.payment.application.service;

import com.core.beautyshop.modules.payment.api.AdminPaymentController;
import com.core.beautyshop.modules.payment.domain.PaymentTransaction;
import com.core.beautyshop.modules.payment.domain.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

public interface AdminPaymentService {
    Page<PaymentTransaction> list(String keyword, TransactionStatus status, Pageable pageable);
    PaymentTransaction getById(Long id);
    AdminPaymentController.Summary summary(Instant from, Instant to);
}
