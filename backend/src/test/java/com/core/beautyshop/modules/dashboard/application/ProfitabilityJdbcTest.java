package com.core.beautyshop.modules.dashboard.application;
import com.core.beautyshop.modules.dashboard.domain.OperatingExpenseRepository;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.time.Instant;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ProfitabilityJdbcTest {
 JdbcTemplate jdbc;ProfitabilityService service;
 final Instant from=Instant.parse("2026-10-01T00:00:00Z"),to=Instant.parse("2026-11-01T00:00:00Z");
 @BeforeEach void schema(){
  jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:profit"+java.util.UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
  jdbc.execute("CREATE TABLE orders(id BIGINT,order_number VARCHAR(50),total_amount DECIMAL(12,2),shipping_fee DECIMAL(12,2),status VARCHAR(30),delivered_at TIMESTAMP,appointment_id BIGINT,service_package_id BIGINT,is_deleted BOOLEAN)");
  jdbc.execute("CREATE TABLE product_returns(order_id BIGINT,status VARCHAR(20),refund_amount DECIMAL(12,2))");
  jdbc.execute("CREATE TABLE stock_allocations(order_number VARCHAR(50),quantity INT,returned_quantity INT,unit_cost_snapshot DECIMAL(12,2),status VARCHAR(20))");
  jdbc.execute("CREATE TABLE operating_expenses(amount DECIMAL(12,2),is_deleted BOOLEAN,incurred_at TIMESTAMP)");
  jdbc.execute("CREATE TABLE procurement_movements(kind VARCHAR(20),amount DECIMAL(12,2),created_at TIMESTAMP)");
  jdbc.execute("CREATE TABLE payment_resolutions(kind VARCHAR(20),amount DECIMAL(12,2),created_at TIMESTAMP)");
  jdbc.execute("CREATE TABLE inventory_consumptions(total_cost DECIMAL(12,2),created_at TIMESTAMP)");
  service=new ProfitabilityService(jdbc,mock(OperatingExpenseRepository.class));
 }
 @Test void productCohortMarginUsesOriginalCostSnapshotsAndCurrentAcceptedReturns(){
  jdbc.execute("INSERT INTO orders VALUES(1,'ORD-1',125,25,'DELIVERED','2026-10-05 10:00:00',NULL,NULL,false),(2,'ORD-SPA',999,0,'COMPLETED',NULL,12,NULL,false),(3,'ORD-LEGACY',700,0,'DELIVERED',NULL,NULL,NULL,false)");
  jdbc.execute("INSERT INTO stock_allocations VALUES('ORD-1',2,1,20,'DEDUCTED')");
  jdbc.execute("INSERT INTO product_returns VALUES(1,'RECEIVED',30),(1,'REJECTED',900)");
  jdbc.execute("INSERT INTO operating_expenses VALUES(15,false,'2026-10-06 00:00:00')");
  jdbc.execute("INSERT INTO procurement_movements VALUES('PAYMENT',200,'2026-10-06 00:00:00'),('SUPPLIER_REFUND',10,'2026-10-07 00:00:00')");
  var report=service.report(from,to);
  money("70",report.deliveredProductNetSales());money("20",report.knownProductCost());money("50",report.productGrossProfit());money("15",report.operatingExpenses());money("200",report.supplierPayments());money("10",report.supplierRecoveries());
  assertEquals(1,report.legacyDeliveriesMissingTime());assertEquals(0,report.allocationsMissingCost());
 }
 @Test void unknownHistoricalCostPreventsAFalseProfitResult(){
  jdbc.execute("INSERT INTO orders VALUES(1,'ORD-1',100,0,'DELIVERED','2026-10-05 10:00:00',NULL,NULL,false)");
  jdbc.execute("INSERT INTO stock_allocations VALUES('ORD-1',1,0,NULL,'DEDUCTED')");
  var report=service.report(from,to);money("100",report.deliveredProductNetSales());assertEquals(1,report.allocationsMissingCost());assertNull(report.productGrossProfit());
 }
 void money(String expected,BigDecimal actual){assertEquals(0,new BigDecimal(expected).compareTo(actual));}
}
