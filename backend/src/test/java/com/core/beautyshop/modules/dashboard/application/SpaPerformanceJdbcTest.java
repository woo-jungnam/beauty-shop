package com.core.beautyshop.modules.dashboard.application;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
class SpaPerformanceJdbcTest {
 JdbcTemplate jdbc;SpaPerformanceReportService service;
 @BeforeEach void schema(){
  jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:spaPerformance"+java.util.UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
  jdbc.execute("CREATE TABLE beauty_services(id BIGINT,name VARCHAR(100))");jdbc.execute("INSERT INTO beauty_services VALUES(1,'Facial'),(2,'Massage')");
  jdbc.execute("CREATE TABLE appointments(id BIGINT,is_deleted BOOLEAN)");jdbc.execute("INSERT INTO appointments VALUES(1,false)");
  jdbc.execute("CREATE TABLE appointment_items(id BIGINT,appointment_id BIGINT,service_id BIGINT,service_name_snapshot VARCHAR(100),ticket_id BIGINT,treatment_session_item_id BIGINT,price DECIMAL(12,2),actual_started_at TIMESTAMP,actual_completed_at TIMESTAMP,ticket_usage_state VARCHAR(30),execution_status VARCHAR(30),is_deleted BOOLEAN)");
  jdbc.execute("CREATE TABLE orders(id BIGINT,total_amount DECIMAL(12,2),paid_amount DECIMAL(12,2),refunded_amount DECIMAL(12,2),paid_at TIMESTAMP,is_deleted BOOLEAN)");
  jdbc.execute("CREATE TABLE spa_visit_invoice_items(order_id BIGINT,appointment_item_id BIGINT,unit_price DECIMAL(12,2),is_deleted BOOLEAN)");
  jdbc.execute("CREATE TABLE user_service_tickets(id BIGINT,order_id BIGINT)");
  jdbc.execute("CREATE TABLE spa_purchase_entitlements(order_id BIGINT,service_id BIGINT,quantity INT)");
  jdbc.execute("CREATE TABLE treatment_financial_consumptions(appointment_item_id BIGINT,kind VARCHAR(30),amount DECIMAL(12,2))");
  jdbc.execute("CREATE TABLE spa_material_usages(appointment_item_id BIGINT,total_cost DECIMAL(12,2),variant_id BIGINT,quantity INT)");
  jdbc.execute("CREATE TABLE spa_appointment_material_plans(appointment_item_id BIGINT,variant_id BIGINT,quantity INT)");
  jdbc.execute("CREATE TABLE spa_material_usage_waivers(appointment_item_id BIGINT)");
  jdbc.execute("CREATE TABLE refund_confirmations(order_id BIGINT,refund_kind VARCHAR(30),amount DECIMAL(12,2))");
  service=new SpaPerformanceReportService(jdbc);
 }
 @Test void separatesInvoicePackageTreatmentAndForfeitAndUsesActualItemMaterialCosts(){
  item(1,1,null,null,"NONE");item(2,1,5L,null,"REDEEMED");item(3,1,6L,31L,"REDEEMED");item(4,1,6L,32L,"FORFEITED");item(5,2,null,null,"NONE");
  jdbc.execute("INSERT INTO orders VALUES(10,100,150,50,'2026-10-01 00:00:00',false),(20,600,650,50,'2026-10-01 00:00:00',false)");
  jdbc.execute("INSERT INTO user_service_tickets VALUES(5,20)");jdbc.execute("INSERT INTO spa_purchase_entitlements VALUES(20,1,2),(20,2,1)");
  jdbc.execute("INSERT INTO spa_visit_invoice_items VALUES(10,1,100,false),(10,3,99,false)");
  jdbc.execute("INSERT INTO refund_confirmations VALUES(10,'OVERPAYMENT',50)");
  jdbc.execute("INSERT INTO treatment_financial_consumptions VALUES(3,'PERFORMED',300),(4,'FORFEIT',400)");
  jdbc.execute("INSERT INTO spa_material_usages VALUES(1,10,100,1),(3,20,100,1)");
  jdbc.execute("INSERT INTO spa_appointment_material_plans VALUES(1,100,1),(3,100,1)");jdbc.execute("INSERT INTO spa_material_usage_waivers VALUES(2)");
  var result=service.report(Instant.parse("2026-10-01T00:00:00Z"),Instant.parse("2026-11-01T00:00:00Z"));var facial=result.services().getFirst();
  assertEquals(3,facial.performedItems());assertEquals(90,facial.performedMinutes());money("100",facial.visitInvoiceRevenue());money("200",facial.ordinaryPackageRevenue());money("300",facial.treatmentRevenue());money("600",facial.earnedRevenue());money("30",facial.recordedMaterialCost());money("570",facial.contributionAfterRecordedMaterials());
  var unbilled=result.services().get(1);assertEquals(1,unbilled.unbilledItems());assertEquals(1,unbilled.untrackedMaterialItems());money("70",unbilled.unbilledSnapshotValue());assertNull(unbilled.contributionAfterRecordedMaterials());
 }
 @Test void missingHistoricalPackageEntitlementsNeverUseCurrentCatalogPrices(){
  item(1,1,5L,null,"REDEEMED");jdbc.execute("INSERT INTO user_service_tickets VALUES(5,20)");jdbc.execute("INSERT INTO orders VALUES(20,600,600,0,'2026-10-01 00:00:00',false)");jdbc.execute("INSERT INTO spa_material_usage_waivers VALUES(1)");
  var row=service.report(Instant.parse("2026-10-01T00:00:00Z"),Instant.parse("2026-11-01T00:00:00Z")).services().getFirst();assertEquals(1,row.missingRevenueItems());money("0",row.earnedRevenue());assertNull(row.contributionAfterRecordedMaterials());
 }
 void item(long id,long serviceId,Long ticket,Long treatment,String usage){jdbc.update("INSERT INTO appointment_items VALUES(?,1,?,'Historical service',?,?,70,'2026-10-05 10:00:00','2026-10-05 10:30:00',?,'PERFORMED',false)",id,serviceId,ticket,treatment,usage);}
 void money(String value,BigDecimal actual){assertEquals(0,new BigDecimal(value).compareTo(actual));}
}
