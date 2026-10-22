package com.core.beautyshop;

import com.core.beautyshop.modules.spa.application.service.SpaFinancialService;
import com.core.beautyshop.modules.spa.domain.Appointment;
import com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus;
import com.core.beautyshop.modules.payment.application.dto.request.SePayWebhookRequest;
import com.core.beautyshop.modules.payment.domain.enums.TransactionStatus;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SpaDepositPaymentIntegrationTest extends SpaVisitCheckoutIntegrityIntegrationTest {
    @Autowired SpaFinancialService finance;
    @Autowired JdbcTemplate jdbc;

    Fixture depositFixture() {
        Fixture f=fixture(false);
        tx(()->{
            var a=entities.find(Appointment.class,f.appointmentId());
            a.setStatus(AppointmentStatus.PENDING);
            jdbc.update("INSERT INTO spa_appointment_finance(appointment_id,policy_version,deposit_required,cancellation_fee,no_show_fee) VALUES(?,1,100,20,40)",f.appointmentId());
            return null;
        });
        return f;
    }
    SpaFinancialService.Finance balance(Fixture f) { return as(f.customer(),()->finance.view(f.appointmentId())); }
    SpaFinancialService.Finance initiate(Fixture f) { return as(f.customer(),()->finance.depositPayment(f.appointmentId())); }
    String bank(String code,int amount,String reference) {
        var request=new SePayWebhookRequest();request.setReferenceCode(reference);request.setAccountNumber(bankAccount);
        request.setTransferType("in");request.setContent(code);request.setTransferAmount(BigDecimal.valueOf(amount));
        webhook.processSePayWebhook(request);return reference;
    }
    @Test void onlyOwnerOrReceptionCanInitializeAndGetIsReadOnly() throws Exception {
        var f=depositFixture();assertNull(balance(f).depositPayment());
        mvc.perform(post("/api/v1/spa/appointments/{id}/deposit-payment",f.appointmentId()).with(authentication(auth(f.other())))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/spa/appointments/{id}/finance",f.appointmentId()).with(authentication(auth(f.customer())))).andExpect(status().isOk()).andExpect(jsonPath("$.data.depositDue").value(100));
        assertNull(balance(f).depositPayment());
        var first=initiate(f);assertEquals(first.depositPayment().transferCode(),initiate(f).depositPayment().transferCode());
        assertTrue(first.depositPayment().paymentInstruction().getQrCodeUrl().contains("amount=100"));
    }
    @Test void partialReplayAndOverpaymentRemainDepositWithoutCreatingOrder() {
        var f=depositFixture();String code=initiate(f).depositPayment().transferCode();long count=orders.count();
        String ref=bank(code,40,UUID.randomUUID().toString());bank(code,999,ref);
        assertEquals(0,balance(f).depositCollected().compareTo(BigDecimal.valueOf(40)));
        assertEquals(0,balance(f).depositDue().compareTo(BigDecimal.valueOf(60)));
        assertTrue(balance(f).depositPayment().paymentInstruction().getQrCodeUrl().contains("amount=60"));
        bank(code,80,UUID.randomUUID().toString());var b=balance(f);
        assertEquals(0,b.depositCollected().compareTo(BigDecimal.valueOf(120)));assertFalse(b.depositPayment().canPay());
        assertTrue(b.reviewReasons().contains("OVERPAYMENT"));assertEquals(count,orders.count());
        assertEquals(AppointmentStatus.PENDING,appointments.findById(f.appointmentId()).orElseThrow().getStatus());
    }
    @Test void concurrentReceiptsAndManualReplayCannotCreditTwice() throws Exception {
        var f=depositFixture();String code=initiate(f).depositPayment().transferCode(),ref=UUID.randomUUID().toString();
        concurrently(()->bank(code,100,ref));
        var receipt=new SpaFinancialService.Receipt(BigDecimal.valueOf(100),"BANK",ref,"Verified bank transfer");
        as(f.receptionist(),()->finance.receipt(f.appointmentId(),receipt,"claim-"+ref,false));
        assertEquals(1,balance(f).entries().size());assertEquals(0,balance(f).depositCollected().compareTo(BigDecimal.valueOf(100)));
        assertNull(balance(f).entries().getFirst().actorId());
    }
    @Test void lateDepositAfterInvoiceIsNotAppliedAgainOnInvoiceReplay() {
        var f=depositFixture();String code=initiate(f).depositPayment().transferCode();bank(code,100,UUID.randomUUID().toString());
        tx(()->{entities.find(Appointment.class,f.appointmentId()).setStatus(AppointmentStatus.COMPLETED);return null;});
        var invoice=invoice(f,PaymentMethod.BANK);assertEquals(0,invoice.paidAmount().compareTo(BigDecimal.valueOf(100)));
        bank(code,50,UUID.randomUUID().toString());
        assertEquals(0,invoice(f,PaymentMethod.BANK).paidAmount().compareTo(BigDecimal.valueOf(100)));
        assertEquals(0,balance(f).refundable().compareTo(BigDecimal.valueOf(50)));
        assertTrue(balance(f).reviewReasons().contains("UNALLOCATED_AFTER_INVOICE"));
    }
    @Test void cancelledAppointmentKeepsActualMoneyRefundableWithoutReopening() {
        var f=depositFixture();String code=initiate(f).depositPayment().transferCode();
        tx(()->{var a=entities.find(Appointment.class,f.appointmentId());a.setStatus(AppointmentStatus.CANCELLED);finance.close(a,false,true);return null;});
        String ref=bank(code,100,UUID.randomUUID().toString());
        assertEquals(TransactionStatus.DEPOSIT_REVIEW,receipts.findByReferenceCode(ref).orElseThrow().getStatus());
        assertEquals(0,balance(f).refundable().compareTo(BigDecimal.valueOf(80)));
        assertThrows(BusinessException.class,()->initiate(f));
        as(f.admin(),()->finance.refund(f.appointmentId(),new SpaFinancialService.Receipt(BigDecimal.valueOf(80),"BANK","OUT-"+ref,"Refund actually transferred"),"refund-"+ref));
        assertEquals(0,balance(f).refundable().signum());
    }
    @Test void ambiguousTargetWrongAccountAndFractionalAmountNeverCredit() {
        var f=depositFixture();String code=initiate(f).depositPayment().transferCode();
        bank(code+" ORD-1234ABCD",100,UUID.randomUUID().toString());
        var request=new SePayWebhookRequest();request.setReferenceCode(UUID.randomUUID().toString());request.setAccountNumber("wrong");request.setTransferType("in");request.setContent(code);request.setTransferAmount(BigDecimal.TEN);
        webhook.processSePayWebhook(request);
        request.setAccountNumber(bankAccount);request.setReferenceCode(UUID.randomUUID().toString());request.setTransferAmount(new BigDecimal("10.5"));webhook.processSePayWebhook(request);
        assertTrue(balance(f).entries().isEmpty());
    }
}
