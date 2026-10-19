package com.core.beautyshop;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

/** Hibernate's test schema knows entity columns; operational append-only JDBC tables need explicit H2 setup. */
final class SpaOperationalJdbcFixture {
 private SpaOperationalJdbcFixture() {}
 static synchronized void ensure(DataSource source) {
  try(var connection=source.getConnection()) {
   if(!connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("H2"))return;
   // A recreated Hibernate schema resets parent IDs, while H2 DB_CLOSE_DELAY=-1
   // retains these JDBC-only tables across evicted Spring contexts.
   try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT (SELECT COUNT(*) FROM users)+(SELECT COUNT(*) FROM appointments)")) {
    rows.next();
    if(rows.getLong(1)==0) {
     for(String table:new String[]{"spa_invoice_advances","spa_financial_entries","spa_appointment_finance","spa_financial_policy",
       "spa_material_usage_waivers","spa_material_usages","spa_appointment_material_plans","spa_service_materials",
       "spa_calendar_exceptions","spa_weekly_hours","spa_calendar_guard"}) {
      try(var cleanup=connection.createStatement()){cleanup.execute("DROP TABLE IF EXISTS "+table+" CASCADE");}
     }
    }
   }
   for(String path:new String[]{"V60__spa_operating_calendar_and_certification.sql","V61__spa_consumable_usage.sql","V62__spa_deposit_and_fee_ledger.sql"}) {
    String sql=new ClassPathResource("db/migration/"+path).getContentAsString(StandardCharsets.UTF_8)
      .replaceAll("(?m)^ALTER TABLE[^;]+;", "")
      .replace("CREATE TABLE ","CREATE TABLE IF NOT EXISTS ")
      .replace("INSERT INTO spa_calendar_guard(id) VALUES (1)","MERGE INTO spa_calendar_guard(id) KEY(id) VALUES(1)")
      .replace("INSERT INTO spa_weekly_hours(day_of_week,closed,opens_at,closes_at) VALUES","MERGE INTO spa_weekly_hours(day_of_week,closed,opens_at,closes_at) KEY(day_of_week) VALUES")
      .replace("INSERT INTO spa_financial_policy(id,version,deposit_amount,cancellation_fee,no_show_fee) VALUES","MERGE INTO spa_financial_policy(id,version,deposit_amount,cancellation_fee,no_show_fee) KEY(id) VALUES");
    new ResourceDatabasePopulator(new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8))).execute(source);
   }
  }catch(Exception failure){throw new IllegalStateException("Cannot prepare isolated Spa JDBC test tables",failure);}
 }
}
