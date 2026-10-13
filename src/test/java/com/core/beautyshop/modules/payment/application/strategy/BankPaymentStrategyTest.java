package com.core.beautyshop.modules.payment.application.strategy;

import com.core.beautyshop.modules.payment.api.dto.PaymentOrderDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class BankPaymentStrategyTest {
    @Test
    void legacyFractionalAmountRoundsInsteadOfTruncatingPaymentInstructions() {
        var strategy = new BankPaymentStrategy();
        ReflectionTestUtils.setField(strategy, "bankAccountName", "SHOP");
        var instruction = strategy.processPayment(PaymentOrderDto.builder().orderNumber("ORD-TEST")
                .totalAmount(new BigDecimal("100000.50")).build());
        assertTrue(instruction.getQrCodeUrl().contains("amount=100001&"));
    }
}
