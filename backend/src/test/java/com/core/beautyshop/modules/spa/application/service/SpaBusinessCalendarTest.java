package com.core.beautyshop.modules.spa.application.service;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.security.services.UserDetailsImpl;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SpaBusinessCalendarTest {
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void holidayChangeCannotInvalidateAlreadyReservedCustomerVisit(){
  var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:calendar_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");var jdbc=new JdbcTemplate(source);
  jdbc.execute("CREATE TABLE spa_calendar_guard(id BIGINT PRIMARY KEY)");jdbc.update("INSERT INTO spa_calendar_guard VALUES(1)");
  jdbc.execute("CREATE TABLE spa_weekly_hours(day_of_week INT,closed BOOLEAN,opens_at TIME,closes_at TIME)");
  for(int day=1;day<=7;day++)jdbc.update("INSERT INTO spa_weekly_hours VALUES(?,FALSE,'08:00','20:00')",day);
  jdbc.execute("CREATE TABLE spa_calendar_exceptions(work_date DATE PRIMARY KEY,closed BOOLEAN,opens_at TIME,closes_at TIME,reason VARCHAR(500),updated_by BIGINT)");
  jdbc.execute("CREATE TABLE appointments(appointment_date DATE,start_time TIME,end_time TIME,is_deleted BOOLEAN,status VARCHAR(30))");
  LocalDate tomorrow=LocalDate.now(SpaTimeRules.ZONE).plusDays(1);
  jdbc.update("INSERT INTO appointments VALUES(?,'10:00','11:00',FALSE,'CONFIRMED')",java.sql.Date.valueOf(tomorrow));
  var principal=new UserDetailsImpl(10L,"admin","admin@test.invalid","unused",List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
  var calendar=new SpaBusinessCalendarService(jdbc);var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
  assertThrows(BusinessException.class,()->tx.execute(status->calendar.saveException(tomorrow,new SpaBusinessCalendarService.ExceptionCommand(true,LocalTime.of(8,0),LocalTime.of(20,0),"Holiday closure"))));
  assertFalse(calendar.forDate(tomorrow).closed());
  assertDoesNotThrow(()->tx.execute(status->calendar.saveException(tomorrow,new SpaBusinessCalendarService.ExceptionCommand(false,LocalTime.of(9,0),LocalTime.of(12,0),"Reduced hours, covered appointments"))));
  assertEquals(LocalTime.of(9,0),calendar.forDate(tomorrow).opensAt());
 }
}
