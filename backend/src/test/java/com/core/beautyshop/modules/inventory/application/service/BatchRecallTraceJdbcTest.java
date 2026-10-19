package com.core.beautyshop.modules.inventory.application.service;
import com.core.beautyshop.modules.inventory.domain.*;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.security.services.UserDetailsImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BatchRecallTraceJdbcTest {
 JdbcTemplate jdbc;BatchRecallService service;BatchRecallRepository recalls;RecallOutreachRepository contacts;AtomicReference<RecallOutreach> contact=new AtomicReference<>();
 @BeforeEach void setup(){
  jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:recall"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
  jdbc.execute("CREATE TABLE users(id BIGINT,full_name VARCHAR(100),phone VARCHAR(20),email VARCHAR(100))");jdbc.execute("INSERT INTO users VALUES(1,'Customer','0900000000','customer@example.test')");
  jdbc.execute("CREATE TABLE warehouse_stocks(id BIGINT,product_variant_id BIGINT,batch_code VARCHAR(100))");jdbc.execute("INSERT INTO warehouse_stocks VALUES(5,10,'LOT'),(7,10,'OTHER')");
  jdbc.execute("CREATE TABLE orders(id BIGINT,order_number VARCHAR(50),user_id BIGINT,customer_name VARCHAR(100),customer_phone VARCHAR(20),customer_email VARCHAR(100),status VARCHAR(30))");jdbc.execute("INSERT INTO orders VALUES(100,'ORD-TEST',1,'Customer','0900000000',NULL,'DELIVERED')");
  jdbc.execute("CREATE TABLE stock_allocations(order_number VARCHAR(50),stock_id BIGINT,quantity INT,returned_quantity INT,status VARCHAR(30))");jdbc.execute("INSERT INTO stock_allocations VALUES('ORD-TEST',5,1,0,'DEDUCTED')");
  jdbc.execute("CREATE TABLE appointments(id BIGINT,user_id BIGINT,status VARCHAR(30))");jdbc.execute("INSERT INTO appointments VALUES(200,1,'COMPLETED')");
  jdbc.execute("CREATE TABLE spa_material_usages(appointment_id BIGINT,appointment_item_id BIGINT,reference VARCHAR(100),allocation_json TEXT,variant_id BIGINT)");
  String snapshot="[{\"stockId\":5,\"batchCode\":\"LOT\",\"expirationDate\":\"2027-01-01\",\"quantity\":2,\"unitCost\":10},{\"stockId\":7,\"batchCode\":\"OTHER\",\"expirationDate\":\"2027-01-01\",\"quantity\":3,\"unitCost\":10}]";
  jdbc.update("INSERT INTO spa_material_usages VALUES(200,201,'SPA-USE',?,10)",snapshot);
  jdbc.execute("CREATE TABLE recall_outreach_events(id BIGINT AUTO_INCREMENT PRIMARY KEY,recall_id BIGINT,subject_type VARCHAR(20),subject_id BIGINT,status VARCHAR(20),outcome VARCHAR(1000),actor_id BIGINT,created_at TIMESTAMP)");
  recalls=mock(BatchRecallRepository.class);var stocks=mock(WarehouseStockRepository.class);contacts=mock(RecallOutreachRepository.class);
  var recall=new BatchRecall();recall.setId(1L);recall.setVariantId(10L);recall.setBatchCode("LOT");when(recalls.findById(1L)).thenReturn(Optional.of(recall));when(recalls.lock(1L)).thenReturn(Optional.of(recall));
  var stock=WarehouseStock.builder().productVariantId(10L).batchCode("LOT").build();stock.setId(5L);when(stocks.findByProductVariantId(10L)).thenReturn(List.of(stock));
  when(contacts.findByRecallId(1L)).thenAnswer(i->contact.get()==null?List.of():List.of(contact.get()));when(contacts.findByRecallIdAndSubjectTypeAndSubjectId(1L,"APPOINTMENT",200L)).thenAnswer(i->Optional.ofNullable(contact.get()));
  when(contacts.saveAndFlush(any())).thenAnswer(i->{contact.set(i.getArgument(0));return contact.get();});
  service=new BatchRecallService(recalls,stocks,jdbc,contacts,new ObjectMapper().findAndRegisterModules());
  var principal=new UserDetailsImpl(9L,"staff","staff@example.test","unused",List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_STAFF")));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
 }
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void tracesBothSoldOrdersAndActualSpaBatchAllocationsWithoutCountingOtherLots(){
  var rows=service.trace(1L);assertEquals(2,rows.size());var spa=rows.stream().filter(r->r.get("subjectType").equals("APPOINTMENT")).findFirst().orElseThrow();assertEquals(2,spa.get("quantity"));assertEquals(200L,spa.get("subjectId"));assertEquals("OPEN",spa.get("outreachStatus"));
 }
 @Test void outreachRequiresAffectedSubjectAndKeepsEveryOutcomeWithItsActor(){
  assertThrows(BusinessException.class,()->service.contact(1L,new BatchRecallService.Contact(999L,"APPOINTMENT","RESOLVED","Wrong visit")));
  service.contact(1L,new BatchRecallService.Contact(200L,"APPOINTMENT","CONTACTED","Customer informed by staff"));service.contact(1L,new BatchRecallService.Contact(200L,"APPOINTMENT","RESOLVED","Customer attended follow-up and outcome recorded"));
  var history=service.history(1L);assertEquals(2,history.size());assertEquals("RESOLVED",history.getFirst().get("status"));assertEquals(9L,history.getFirst().get("actorId"));assertEquals("CONTACTED",history.getLast().get("status"));
  assertEquals("RESOLVED",service.trace(1L).stream().filter(r->r.get("subjectType").equals("APPOINTMENT")).findFirst().orElseThrow().get("outreachStatus"));
 }
}
