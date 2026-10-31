package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.security.services.UserDetailsImpl;
import jakarta.persistence.EntityManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpaFinancialPolicyTest {
 SpaFinancialService finance; EntityManager em; OrderFacade orders; Appointment appointment;
 @BeforeEach void setup() {
  var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:spa_finance_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
  var jdbc=new JdbcTemplate(source);
  jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY)");jdbc.update("INSERT INTO users VALUES(1),(10)");
  jdbc.execute("CREATE TABLE appointments(id BIGINT PRIMARY KEY)");jdbc.update("INSERT INTO appointments VALUES(20)");
  jdbc.execute("CREATE TABLE orders(id BIGINT PRIMARY KEY)");
  new ResourceDatabasePopulator(new ClassPathResource("db/migration/V62__spa_deposit_and_fee_ledger.sql")).execute(source);
  jdbc.execute("ALTER TABLE spa_appointment_finance ADD COLUMN deposit_transfer_code VARCHAR(39) UNIQUE");
  jdbc.execute("ALTER TABLE spa_financial_entries ALTER COLUMN actor_id DROP NOT NULL");
  jdbc.execute("ALTER TABLE spa_financial_entries ADD COLUMN payment_transaction_id BIGINT UNIQUE");
  var access=mock(SpaAccessService.class);when(access.canManageReception()).thenReturn(true);when(access.hasRole("ADMIN")).thenReturn(true);
  em=mock(EntityManager.class);orders=mock(OrderFacade.class);finance=new SpaFinancialService(new JdbcTemplate(source),em,access,orders);
  var principal=new UserDetailsImpl(10L,"cashier","cashier@test.invalid","unused",List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
  var item=AppointmentItem.builder().price(new BigDecimal("300000")).build();
  appointment=Appointment.builder().userId(1L).status(AppointmentStatus.PENDING).items(new ArrayList<>(List.of(item))).build();appointment.setId(20L);item.setAppointment(appointment);
  when(em.find(eq(Appointment.class),eq(20L),any(jakarta.persistence.LockModeType.class))).thenReturn(appointment);
  finance.savePolicy(new SpaFinancialService.Policy(1,new BigDecimal("100000"),new BigDecimal("20000"),new BigDecimal("40000")));
 }
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 private SpaFinancialService.Receipt receipt(String amount,String reference){return new SpaFinancialService.Receipt(new BigDecimal(amount),"CASH",reference,"Confirmed counter receipt");}
 private void snapshot(){finance.snapshot(appointment,true,2);}
 @Test void changedPolicyRequiresExactCustomerAcceptanceBeforeRightsAreOpened(){
  assertThrows(BusinessException.class,()->finance.snapshot(appointment,true,1));
  assertThrows(BusinessException.class,()->finance.snapshot(appointment,false,2));snapshot();
  assertThrows(BusinessException.class,()->finance.requireDeposit(appointment));
  finance.receipt(20L,receipt("100000","CASH-1"),"deposit-1",false);assertDoesNotThrow(()->finance.requireDeposit(appointment));
 }
 @Test void cancellationBalancesDepositFeeAndManualRefundWithoutDoubleCollection(){
  snapshot();finance.receipt(20L,receipt("100000","CASH-1"),"deposit-1",false);
  finance.receipt(20L,receipt("100000","CASH-1"),"deposit-1",false);
  assertThrows(BusinessException.class,()->finance.receipt(20L,receipt("1","CASH-2"),"deposit-2",false));
  finance.close(appointment,false,true);var balance=finance.view(20L);
  assertEquals(0,balance.feeDue().compareTo(BigDecimal.ZERO));assertEquals(0,balance.refundable().compareTo(new BigDecimal("80000")));
  assertThrows(BusinessException.class,()->finance.refund(20L,receipt("80001","REF-2"),"refund-too-much"));
  finance.refund(20L,receipt("80000","REF-1"),"refund-1");finance.refund(20L,receipt("80000","REF-1"),"refund-1");
  assertEquals(0,finance.view(20L).refundable().signum());assertEquals(2,finance.view(20L).entries().size());
 }
 @Test void invoiceAllocatesExistingDepositOnceAndLeavesSkippedValueRefundable(){
  snapshot();finance.receipt(20L,receipt("100000","CASH-1"),"deposit-1",false);
  finance.applyAdvance(appointment,new BigDecimal("60000"));finance.applyAdvance(appointment,new BigDecimal("60000"));
  verify(orders,times(1)).applySpaVisitAdvance(20L,new BigDecimal("60000"),"SPA-ADVANCE-20");
  assertEquals(0,finance.view(20L).refundable().compareTo(new BigDecimal("40000")));
 }
 @Test void payFullPrepaymentCalculates100PercentPayableAndAutoConfirmsOnFullDeposit(){
  BigDecimal depositReq = finance.snapshot(appointment, true, 2, "PAY_FULL", false);
  assertEquals(0, depositReq.compareTo(new BigDecimal("300000")));
  assertEquals(AppointmentStatus.PENDING, appointment.getStatus());
  finance.receipt(20L, receipt("300000", "CASH-FULL"), "full-deposit-1", false);
  assertEquals(AppointmentStatus.CONFIRMED, appointment.getStatus());
  assertNull(appointment.getPendingExpiresAt());
 }
 @Test void waiveWalkInAllowsZeroDepositForAdmin(){
  BigDecimal depositReq = finance.snapshot(appointment, true, 2, "WAIVE_WALK_IN", true);
  assertEquals(0, depositReq.compareTo(BigDecimal.ZERO));
  assertDoesNotThrow(() -> finance.requireDeposit(appointment));
 }
}
