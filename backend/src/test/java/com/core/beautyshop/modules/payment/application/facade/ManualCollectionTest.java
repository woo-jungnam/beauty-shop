package com.core.beautyshop.modules.payment.application.facade;

import com.core.beautyshop.modules.payment.application.strategy.PaymentStrategyFactory;
import com.core.beautyshop.modules.payment.domain.PaymentTransaction;
import com.core.beautyshop.modules.payment.domain.PaymentTransactionRepository;
import com.core.beautyshop.modules.payment.domain.enums.TransactionStatus;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManualCollectionTest {
    private final PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
    private final PaymentFacadeImpl service = new PaymentFacadeImpl(mock(PaymentStrategyFactory.class), transactions);

    @Test void createsAnIncomingReceiptWithoutClaimingItIsCashOrBank() {
        assertTrue(service.recordManualCollection("ORD-1", new BigDecimal("700"), "DOC-1", 7L));
        var captured = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactions).saveAndFlush(captured.capture());
        var receipt = captured.getValue();
        assertEquals("ORD-1", receipt.getOrderNumber());
        assertEquals(new BigDecimal("700"), receipt.getAmount());
        assertEquals("in", receipt.getTransferType());
        assertEquals("MANUAL", receipt.getGateway());
        assertEquals(TransactionStatus.SUCCESS, receipt.getStatus());
        assertTrue(receipt.getRawPayload().contains("\"receivedBy\":7"));
    }

    @Test void anExistingBankReceiptCannotBeCountedAgainManually() {
        when(transactions.findByReferenceCode("BANK-1")).thenReturn(Optional.of(
                PaymentTransaction.builder().orderNumber("ORD-1").gateway("SEPAY").build()));
        assertThrows(BusinessException.class, () -> service.recordManualCollection("ORD-1", new BigDecimal("700"), "BANK-1", 7L));
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test void invalidAmountsAndMissingEvidenceCannotCreateReceipts() {
        assertThrows(BusinessException.class, () -> service.recordManualCollection("ORD-1", BigDecimal.ZERO, "DOC-1", 7L));
        assertThrows(BusinessException.class, () -> service.recordManualCollection("ORD-1", new BigDecimal("1.5"), "DOC-1", 7L));
        assertThrows(BusinessException.class, () -> service.recordManualCollection("ORD-1", BigDecimal.ONE, "", 7L));
        assertThrows(BusinessException.class, () -> service.recordManualCollection("ORD-1", BigDecimal.ONE, "DOC-1", null));
        verifyNoInteractions(transactions);
    }
}
