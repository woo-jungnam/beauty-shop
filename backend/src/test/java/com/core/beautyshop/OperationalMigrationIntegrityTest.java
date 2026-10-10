package com.core.beautyshop;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

/** Upgrade an isolated real MySQL schema with operational data, rather than Hibernate create/drop. */
@Testcontainers(disabledWithoutDocker = true)
class OperationalMigrationIntegrityTest {
    @Container static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withCommand("--log-bin-trust-function-creators=1");

    Flyway flyway(String target) {
        return Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration").cleanDisabled(false).target(target).load();
    }

    Connection connection() throws SQLException {
        return DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    }

    @BeforeEach
    void prepareVersion26() {
        flyway("26").clean();
        flyway("26").migrate();
    }

    void execute(String... statements) throws SQLException {
        try (var connection = connection(); var statement = connection.createStatement()) {
            for (String sql : statements) statement.execute(sql);
        }
    }

    String scalar(String sql) throws SQLException {
        try (var connection = connection(); var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next()); return result.getString(1);
        }
    }

    void seedCatalogAndWarehouse() throws SQLException {
        execute("INSERT INTO products(id,name,slug,base_price) VALUES (90001,'Legacy product','legacy-upgrade-90001',90.50)",
                "INSERT INTO product_variants(id,product_id,sku,variant_name,price,is_default) VALUES (90001,90001,'LEGACY-SKU','Legacy variant',90.50,TRUE)",
                "INSERT INTO warehouses(id,name,code,warehouse_type) VALUES (90001,'Legacy warehouse','LEGACY-90001','BRANCH')");
    }

    @Test
    void upgradePreservesOperationalIdsBalancesReferencesAndBackfillsOnlyKnownSettlementTimes() throws Exception {
        seedCatalogAndWarehouse();
        execute("INSERT INTO products(id,name,slug,base_price) VALUES (90002,'Product without variants','legacy-no-sku-90002',250)",
                "INSERT INTO warehouse_stocks(id,warehouse_id,product_variant_id,quantity,reserved_quantity,quarantined_quantity,batch_code,cost_price) VALUES (90001,90001,90001,10,2,1,' LOT-LEGACY ',30)",
                "INSERT INTO warehouse_stocks(id,warehouse_id,product_variant_id,quantity,reserved_quantity,batch_code) VALUES (90002,90001,90001,4,0,NULL)",
                "INSERT INTO orders(id,order_number,status,payment_method,payment_status,customer_name,customer_phone,shipping_address,sub_total,total_amount,paid_amount) VALUES (90001,'ORD-LEGACY-BANK','PROCESSING','BANK','PAID','Legacy','0987654321','Address',200,200,200), (90002,'ORD-LEGACY-COD','DELIVERED','COD','PAID','Legacy','0987654321','Address',100,100,100), (90003,'ORD-UNKNOWN-TIME','PENDING','BANK','PAID','Legacy','0987654321','Address',100,100,100)",
                "INSERT INTO order_items(id,order_id,product_variant_id,sku,product_name,quantity,price) VALUES (90001,90001,90001,'LEGACY-SKU','Original product snapshot',2,90.50)",
                "INSERT INTO stock_allocations(id,order_number,stock_id,variant_id,quantity,status) VALUES (90001,'ORD-LEGACY-BANK',90001,90001,2,'RESERVED')",
                "INSERT INTO payment_transactions(order_number,reference_code,gateway,transfer_type,amount,raw_payload,status,created_at) VALUES ('ORD-LEGACY-BANK','LEGACY-PAID','SEPAY','in',200,'{}','SUCCESS','2026-09-01 10:00:00')",
                "INSERT INTO order_status_histories(order_id,status,created_at) VALUES (90002,'DELIVERED','2026-09-02 10:00:00')",
                "ALTER TABLE order_items DROP COLUMN image_url"); // Simulate a legacy V1 without the later image snapshot addition.
        flyway("latest").migrate();

        assertEquals("Legacy product", scalar("SELECT name FROM products WHERE id=90001"));
        assertEquals("LEGACY-SKU", scalar("SELECT sku FROM product_variants WHERE id=90001"));
        assertEquals("90.50", scalar("SELECT price FROM product_variants WHERE id=90001"));
        assertEquals("10:2:1:LOT-LEGACY", scalar("SELECT CONCAT(quantity,':',reserved_quantity,':',quarantined_quantity,':',batch_code) FROM warehouse_stocks WHERE id=90001"));
        assertEquals("", scalar("SELECT batch_code FROM warehouse_stocks WHERE id=90002"));
        assertEquals("Original product snapshot:90001", scalar("SELECT CONCAT(product_name,':',product_variant_id) FROM order_items WHERE id=90001"));
        assertEquals("90001:90001:2:RESERVED", scalar("SELECT CONCAT(stock_id,':',variant_id,':',quantity,':',status) FROM stock_allocations WHERE id=90001"));
        assertEquals("250.00", scalar("SELECT price FROM product_variants WHERE product_id=90002 AND sku='MIG-V28-90002'"));
        assertEquals("2026-09-01 10:00:00", scalar("SELECT DATE_FORMAT(paid_at,'%Y-%m-%d %H:%i:%s') FROM orders WHERE id=90001"));
        assertEquals("2026-09-02 10:00:00", scalar("SELECT DATE_FORMAT(paid_at,'%Y-%m-%d %H:%i:%s') FROM orders WHERE id=90002"));
        assertNull(scalar("SELECT paid_at FROM orders WHERE id=90003"));
        assertEquals("1", scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='order_items' AND column_name='image_url'"));
        try (var connection = connection(); var statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.execute("INSERT INTO warehouse_stocks(warehouse_id,product_variant_id,quantity,batch_code) VALUES (90001,90001,3,'')"));
        }
    }

    @Test
    void ambiguousNullAndBlankBatchesRejectUpgradeWithoutDeletingStockOrAllocations() throws Exception {
        seedCatalogAndWarehouse();
        execute("INSERT INTO warehouse_stocks(id,warehouse_id,product_variant_id,quantity,reserved_quantity,batch_code) VALUES (90001,90001,90001,10,2,NULL), (90002,90001,90001,7,0,'   ')",
                "INSERT INTO stock_allocations(order_number,stock_id,variant_id,quantity,status) VALUES ('ORD-LEGACY',90001,90001,2,'RESERVED')");
        var failure = assertThrows(org.flywaydb.core.api.FlywayException.class, () -> flyway("latest").migrate());
        assertTrue(failure.getMessage().contains("Duplicate normalized stock batches"));
        assertEquals("17", scalar("SELECT SUM(quantity) FROM warehouse_stocks WHERE warehouse_id=90001"));
        assertEquals("1", scalar("SELECT COUNT(*) FROM stock_allocations WHERE stock_id=90001"));
        assertEquals("2", scalar("SELECT reserved_quantity FROM warehouse_stocks WHERE id=90001"));
    }

    void seedVersion30SpaReservations() throws Exception {
        flyway("30").migrate();
        execute("INSERT INTO users(id,username,full_name,email,status) VALUES (91001,'spa-upgrade-91001','Spa upgrade customer','spa-upgrade-91001@example.test','ACTIVE')",
                "INSERT INTO beauty_services(id,name,slug,base_price,duration_minutes,preparation_time_minutes) VALUES (91001,'Original facial','spa-upgrade-facial-91001',300000,45,15)",
                "INSERT INTO service_packages(id,name,price,validity_days) VALUES (91001,'Original three visits',900000,30)",
                "INSERT INTO service_package_items(id,package_id,service_id,quantity) VALUES (91001,91001,91001,3)",
                "INSERT INTO orders(id,order_number,user_id,service_package_id,status,payment_method,payment_status,customer_name,customer_phone,shipping_address,sub_total,total_amount,paid_amount,paid_at) VALUES (91001,'ORD-SPA-UPGRADE-91001',91001,91001,'PROCESSING','BANK','PAID','Spa upgrade customer','0987654321','Spa appointment',900000,900000,900000,CURRENT_TIMESTAMP)",
                "INSERT INTO spa_purchase_snapshots(order_id,package_id,validity_days) VALUES (91001,91001,30)",
                "INSERT INTO spa_purchase_entitlements(order_id,service_id,quantity) VALUES (91001,91001,3)",
                "INSERT INTO user_service_tickets(id,user_id,package_id,order_id,total_sessions,used_sessions,expiry_date,status) VALUES (91001,91001,91001,91001,3,3,DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 30 DAY),'COMPLETED')",
                "INSERT INTO ticket_entitlements(ticket_id,service_id,total_sessions,used_sessions) VALUES (91001,91001,3,3)",
                "INSERT INTO appointments(id,user_id,appointment_date,start_time,end_time,status) VALUES (91001,91001,DATE_ADD(CURRENT_DATE, INTERVAL 3 DAY),'10:00:00','11:00:00','PENDING'), (91002,91001,DATE_ADD(CURRENT_DATE, INTERVAL 4 DAY),'10:00:00','11:00:00','CONFIRMED'), (91003,91001,DATE_SUB(CURRENT_DATE, INTERVAL 2 DAY),'10:00:00','11:00:00','COMPLETED')",
                "INSERT INTO appointment_items(id,appointment_id,service_id,ticket_id,price,start_time,end_time) VALUES (91001,91001,91001,91001,0,'10:00:00','11:00:00'), (91002,91002,91001,91001,0,'10:00:00','11:00:00'), (91003,91003,91001,91001,0,'10:00:00','11:00:00')");
    }

    @Test
    void version31MovesOnlyProvableReservationsAndDoesNotInventHistoricalPerformance() throws Exception {
        seedVersion30SpaReservations();
        flyway("31").migrate();

        assertEquals("1:2:ACTIVE:BOOKING_TIME", scalar("SELECT CONCAT(used_sessions,':',reserved_sessions,':',status,':',expiry_check_mode) FROM user_service_tickets WHERE id=91001"));
        assertEquals("1:2", scalar("SELECT CONCAT(used_sessions,':',reserved_sessions) FROM ticket_entitlements WHERE ticket_id=91001 AND service_id=91001"));
        assertEquals("2", scalar("SELECT COUNT(*) FROM appointment_items WHERE id IN (91001,91002) AND execution_status='PLANNED' AND ticket_usage_state='RESERVED'"));
        assertEquals("LEGACY_FINALIZED:LEGACY_FINALIZED", scalar("SELECT CONCAT(execution_status,':',ticket_usage_state) FROM appointment_items WHERE id=91003"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM appointment_items WHERE id IN (91001,91002,91003) AND (actual_started_at IS NOT NULL OR actual_completed_at IS NOT NULL OR performed_by_user_id IS NOT NULL)"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM appointments WHERE id IN (91001,91002,91003) AND (checked_in_at IS NOT NULL OR checked_in_by_user_id IS NOT NULL OR actual_started_at IS NOT NULL OR actual_completed_at IS NOT NULL)"));
        assertEquals("LEGACY_ADJUSTMENT:1:2:0", scalar("SELECT CONCAT(operation,':',used_after,':',reserved_after,':',available_after) FROM ticket_session_movements WHERE idempotency_key='V31:TICKET:91001'"));
        assertEquals("BOOKING_TIME", scalar("SELECT expiry_check_mode FROM spa_purchase_snapshots WHERE order_id=91001"));
        try (var connection = connection(); var statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.execute("UPDATE user_service_tickets SET used_sessions=2 WHERE id=91001"));
            assertThrows(SQLException.class, () -> statement.execute("UPDATE ticket_entitlements SET used_sessions=2 WHERE ticket_id=91001 AND service_id=91001"));
        }
    }

    @Test
    void missingOrInsufficientEntitlementStopsVersion31BeforeCountersAndApplicationSchemaChange() throws Exception {
        seedVersion30SpaReservations();
        for (boolean missing : new boolean[]{true, false}) {
            execute("DELETE FROM ticket_entitlements WHERE ticket_id=91001 AND service_id=91001");
            if (!missing) {
                execute("INSERT INTO ticket_entitlements(ticket_id,service_id,total_sessions,used_sessions) VALUES (91001,91001,3,1)",
                        "UPDATE user_service_tickets SET used_sessions=1 WHERE id=91001");
                flyway("31").repair(); // Retry the failed preflight after changing the fixture, without applying any schema changes.
            }
            var failure = assertThrows(org.flywaydb.core.api.FlywayException.class, () -> flyway("31").migrate());
            assertTrue(failure.getMessage().contains("reconcile historical ticket reservations"), failure.getMessage());
            assertEquals(missing ? "3:COMPLETED" : "1:COMPLETED", scalar("SELECT CONCAT(used_sessions,':',status) FROM user_service_tickets WHERE id=91001"));
            assertEquals(missing ? "0" : "1", scalar("SELECT COUNT(*) FROM ticket_entitlements WHERE ticket_id=91001 AND service_id=91001"));
            if (!missing) assertEquals("1", scalar("SELECT used_sessions FROM ticket_entitlements WHERE ticket_id=91001 AND service_id=91001"));
            assertEquals("3", scalar("SELECT COUNT(*) FROM appointment_items WHERE id IN (91001,91002,91003)"));
            assertEquals("0", scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND ((table_name='user_service_tickets' AND column_name='reserved_sessions') OR (table_name='appointment_items' AND column_name='execution_status') OR (table_name='appointments' AND column_name='checked_in_at'))"));
            assertEquals("0", scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('ticket_session_movements','appointment_action_history')"));
        }
    }
}
