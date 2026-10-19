package com.core.beautyshop.modules.spa.application.service;
import com.core.beautyshop.modules.order.application.service.SpaVisitOrderService;
import com.core.beautyshop.modules.order.domain.*;
import com.core.beautyshop.modules.order.domain.enums.*;
import com.core.beautyshop.modules.payment.api.PaymentFacade;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import com.core.beautyshop.shared.exception.BusinessException;
import jakarta.persistence.EntityManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SpaVisitAdvanceTest {
 @Test void depositReducesAmountDueOnceAndNeverDiscountsPerformedServicePrices(){
  var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:advance_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");var jdbc=new JdbcTemplate(source);
  jdbc.execute("CREATE TABLE spa_invoice_advances(reference VARCHAR(128) PRIMARY KEY,order_id BIGINT,amount DECIMAL(12,0))");
  var orders=mock(OrderRepository.class);var payments=mock(PaymentFacade.class);var em=mock(EntityManager.class);
  var invoice=Order.builder().orderNumber("ORD-TEST").appointmentId(10L).userId(1L).status(OrderStatus.COMPLETED)
    .paymentMethod(PaymentMethod.CASH).paymentStatus(PaymentStatus.PENDING).subTotal(new BigDecimal("300000"))
    .totalAmount(new BigDecimal("300000")).spaVisitItems(new ArrayList<>()).statusHistories(new ArrayList<>()).build();invoice.setId(3L);
  when(orders.findIdByAppointmentId(10L)).thenReturn(Optional.of(3L));when(orders.findByIdForUpdate(3L)).thenReturn(Optional.of(invoice));
  var service=new SpaVisitOrderService(orders,payments,em,jdbc);
  var events=mock(org.springframework.context.ApplicationEventPublisher.class);
  org.springframework.test.util.ReflectionTestUtils.setField(service,"events",events);
  var first=service.applyAdvance(10L,new BigDecimal("100000"),"confirmed-deposit-10");
  var replay=service.applyAdvance(10L,new BigDecimal("100000"),"confirmed-deposit-10");
  assertEquals(0,first.totalAmount().compareTo(new BigDecimal("300000")));assertEquals(0,replay.paidAmount().compareTo(new BigDecimal("100000")));
  assertEquals(0,replay.amountDue().compareTo(new BigDecimal("200000")));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM spa_invoice_advances",Integer.class));
  assertThrows(BusinessException.class,()->service.applyAdvance(10L,new BigDecimal("50000"),"confirmed-deposit-10"));
  assertThrows(BusinessException.class,()->service.applyAdvance(10L,new BigDecimal("200001"),"excess-deposit"));
  verifyNoInteractions(payments);
  verify(events,times(1)).publishEvent(new com.core.beautyshop.modules.identity.api.event.LoyaltyEvents.EligibleSpendChanged(3L,1L,new BigDecimal("300000")));
 }
}
