package com.core.beautyshop.modules.payment.api.dto;

import java.math.BigDecimal;

public record BankReceipt(Long transactionId, String reference, BigDecimal amount, String target) {}
