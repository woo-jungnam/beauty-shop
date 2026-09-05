package com.core.beautyshop.modules.payment.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    boolean existsByReferenceCode(String referenceCode);

    Optional<PaymentTransaction> findByReferenceCode(String referenceCode);

    Page<PaymentTransaction> findByOrderNumberOrderByCreatedAtDesc(String orderNumber, Pageable pageable);
}
