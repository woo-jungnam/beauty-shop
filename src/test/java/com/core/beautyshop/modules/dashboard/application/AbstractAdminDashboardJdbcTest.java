package com.core.beautyshop.modules.dashboard.application;

import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Executes the service's SQL against real test tables, without mock aggregates. */
abstract class AbstractAdminDashboardJdbcTest {
    JdbcTemplate jdbc;
    AdminDashboardService service;
    abstract JdbcTemplate connect();
    abstract boolean supportsMySqlGrouping();

    @BeforeEach
    void schema() {
        jdbc = connect();
        for (String table : List.of("orders", "payment_transactions", "refund_confirmations", "users", "roles", "user_roles", "warehouse_stocks", "staff_schedules", "appointments", "appointment_items")) jdbc.execute("DROP TABLE IF EXISTS " + table);
        jdbc.execute("CREATE TABLE orders (id BIGINT PRIMARY KEY, created_at TIMESTAMP, paid_at TIMESTAMP, total_amount DECIMAL(14,2), paid_amount DECIMAL(14,2), status VARCHAR(30), payment_status VARCHAR(30), is_deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE payment_transactions (id BIGINT PRIMARY KEY, created_at TIMESTAMP, amount DECIMAL(14,2), gateway VARCHAR(30), transfer_type VARCHAR(20), status VARCHAR(40))");
        jdbc.execute("CREATE TABLE refund_confirmations (id BIGINT PRIMARY KEY, confirmed_at TIMESTAMP, amount DECIMAL(14,2))");
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, status VARCHAR(30), is_deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE roles (id BIGINT PRIMARY KEY, name VARCHAR(50), is_deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE user_roles (user_id BIGINT, role_id BIGINT)");
        jdbc.execute("CREATE TABLE warehouse_stocks (id BIGINT PRIMARY KEY, product_variant_id BIGINT, quantity INT, reserved_quantity INT, min_quantity INT, cost_price DECIMAL(14,2), is_deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE staff_schedules (id BIGINT PRIMARY KEY, work_date DATE, start_time TIME, end_time TIME, status VARCHAR(30), is_deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE appointments (id BIGINT PRIMARY KEY, appointment_date DATE, status VARCHAR(30), is_deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE appointment_items (id BIGINT PRIMARY KEY, appointment_id BIGINT, staff_id BIGINT, start_time TIME, end_time TIME, is_deleted BOOLEAN, execution_status VARCHAR(30) DEFAULT 'PLANNED', actual_started_at TIMESTAMP NULL, actual_completed_at TIMESTAMP NULL)");
        service = new AdminDashboardService(jdbc);
    }

    @Test
    void overviewUsesCashLedgerPaidTimeCustomerRoleAndWholeSkuStock() {
        jdbc.execute("INSERT INTO orders VALUES (1,'2026-09-30 10:00:00','2026-10-02 10:00:00',100,100,'DELIVERED','PAID',false), (2,'2026-09-30 10:00:00','2026-10-02 11:00:00',80,80,'CANCELLED','REFUND_PENDING',false), (3,'2026-10-02 10:00:00','2026-10-02 10:00:00',0,0,'CONFIRMED','PAID',false), (4,'2026-10-02 10:00:00',NULL,500,0,'PENDING','PENDING',false)");
        receipt(1, "2026-10-02 10:00:00", 100, "SEPAY", "in", "SUCCESS");
        receipt(2, "2026-10-02 10:00:00", 40, "SEPAY", "in", "PARTIALLY_PAID");
        receipt(3, "2026-10-02 10:00:00", 60, "COD", "in", "SUCCESS");
        receipt(4, "2026-10-02 10:00:00", 20, "SEPAY", "in", "CANCELLED_ORDER_RECEIVED");
        receipt(5, "2026-10-02 10:00:00", 30, "SEPAY", "in", "ORDER_NOT_FOUND");
        receipt(6, "2026-10-02 10:00:00", 10, "SEPAY", "in", "IGNORED");
        receipt(7, "2026-10-02 10:00:00", 900, "SEPAY", "in", "FAILED");
        receipt(8, "2026-10-02 10:00:00", 1000, "SEPAY", "out", "IGNORED");
        receipt(9, "2026-10-02 10:00:00", -500, "SEPAY", "in", "SUCCESS");
        receipt(11,"2026-10-02 10:00:00",50,"CASH","in","SUCCESS");
        receipt(10, "2026-10-04 00:00:00", 200, "SEPAY", "in", "SUCCESS");
        jdbc.execute("INSERT INTO refund_confirmations VALUES (1,'2026-10-03 10:00:00',25),(2,'2026-10-04 00:00:00',50)");
        jdbc.execute("INSERT INTO users VALUES (1,'ACTIVE',false),(2,'ACTIVE',false),(3,'BLOCKED',false),(4,'ACTIVE',false),(5,'ACTIVE',true)");
        jdbc.execute("INSERT INTO roles VALUES (1,'ROLE_CUSTOMER',false),(2,'ROLE_ADMIN',false),(3,'ROLE_STAFF',false)");
        jdbc.execute("INSERT INTO user_roles VALUES (1,1),(1,2),(2,2),(3,1),(4,3),(5,1)");
        jdbc.execute("INSERT INTO warehouse_stocks VALUES (1,1,3,0,5,1,false),(2,1,8,0,5,1,false),(3,2,2,0,5,1,false),(4,2,3,0,5,1,false),(5,3,0,0,5,1,true)");
        var result = service.overview(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-04T00:00:00Z"));
        assertEquals(2, result.totalOrders());
        assertEquals(3, result.paidOrders());
        assertMoney("60", result.averageOrderValue());
        assertMoney("310", result.revenue());
        assertMoney("25", result.refundedAmount());
        assertMoney("60", result.codAmount());
        assertMoney("200", result.bankAmount());
        assertMoney("50", result.cashAmount());
        assertEquals(7, result.receiptCount());
        assertEquals(1, result.activeCustomers());
        assertEquals(1, result.lowStockItems());
        assertMoney("16", result.inventoryValue());
        assertEquals(1, result.pendingPaymentOrders());
    }

    @Test
    void dailyAndMonthlyBucketsUseReceiptTimeBusinessZoneAndRefundOnlyDays() {
        assumeTrue(supportsMySqlGrouping(), "DATE_FORMAT/CONVERT_TZ are verified by the MySQL subclass");
        jdbc.execute("INSERT INTO orders VALUES (1,'2026-09-28 10:00:00','2026-09-30 18:00:00',100,100,'CANCELLED','REFUND_PENDING',false)");
        receipt(1, "2026-09-30 18:00:00", 100, "SEPAY", "in", "SUCCESS");
        receipt(2, "2026-10-01 10:00:00", 60, "COD", "in", "SUCCESS");
        receipt(3, "2026-10-01 10:00:00", 5, "SEPAY", "in", "IGNORED");
        receipt(4, "2026-10-02 10:00:00", 10, "SEPAY", "in", "ORDER_NOT_FOUND");
        receipt(5, "2026-10-02 17:00:00", 300, "SEPAY", "in", "SUCCESS");
        receipt(6, "2026-10-01 10:00:00", 400, "SEPAY", "out", "IGNORED");
        jdbc.execute("INSERT INTO refund_confirmations VALUES (1,'2026-10-01 18:00:00',7),(2,'2026-10-02 18:00:00',3)");
        Instant from = Instant.parse("2026-09-30T17:00:00Z"), to = Instant.parse("2026-10-02T17:00:00Z");
        var daily = service.revenue(from, to, "day");
        assertEquals(List.of("2026-10-01", "2026-10-02"), daily.stream().map(AdminDashboardService.RevenuePoint::period).toList());
        assertMoney("165", daily.getFirst().revenue());
        assertMoney("10", daily.getLast().revenue());
        assertEquals(1, daily.getFirst().orderCount());
        assertMoney("7", daily.getLast().refundedAmount());
        assertEquals(0, daily.getLast().orderCount());
        var monthly = service.revenue(from, to, "month");
        assertEquals(1, monthly.size());
        assertEquals("2026-10", monthly.getFirst().period());
        assertMoney("175", monthly.getFirst().revenue());
        assertMoney("7", monthly.getFirst().refundedAmount());
        assertMoney("60", monthly.getFirst().codAmount());
        assertMoney("115", monthly.getFirst().bankAmount());
        assertEquals(4, monthly.getFirst().receiptCount());
    }

    @Test
    void spaSeparatesAssignedBookingsServedNoShowsAndShowsOverCapacity() {
        jdbc.execute("INSERT INTO staff_schedules VALUES (1,'2026-10-02','08:00:00','09:00:00','SCHEDULED',false),(2,'2026-10-02','08:00:00','11:00:00','SCHEDULED',true),(3,'2026-10-02','08:00:00','12:00:00','OFF',false)");
        String[] states = {"CONFIRMED", "IN_PROGRESS", "COMPLETED", "PENDING", "CANCELLED", "NO_SHOW", "CONFIRMED"};
        for (int index = 0; index < states.length; index++) {
            int id = index + 1;
            jdbc.update("INSERT INTO appointments VALUES (?,'2026-10-02',?,false)", id, states[index]);
            jdbc.update("INSERT INTO appointment_items (id,appointment_id,staff_id,start_time,end_time,is_deleted) VALUES (?,?,?,'10:00:00','11:00:00',false)", id, id, id == 7 ? null : 1);
        }
        jdbc.execute("UPDATE appointment_items SET execution_status='PERFORMED', actual_started_at='2026-10-02 10:00:00', actual_completed_at='2026-10-02 10:45:00' WHERE id=3");
        var result = service.spaOccupancy(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 2));
        assertEquals(60, result.scheduledMinutes());
        assertEquals(180, result.bookedMinutes());
        assertEquals(45, result.servedMinutes());
        assertEquals(180, result.reservedMinutes());
        assertEquals(0, result.legacyFinalizedMinutes());
        assertEquals(60, result.noShowMinutes());
        assertEquals(1, result.noShowAppointments());
        assertMoney("300", result.occupancyRatePercent());
        assertMoney("75", result.servedRatePercent());
        assertEquals(120, result.overCapacityMinutes());
        assertTrue(result.overCapacity());
        var noCapacity = service.spaOccupancy(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 3));
        assertEquals(0, noCapacity.scheduledMinutes());
        assertFalse(noCapacity.overCapacity());
    }

    @Test
    void rejectsReversedOrEmptyRangesAndUnsupportedPeriods() {
        Instant time = Instant.parse("2026-10-02T00:00:00Z");
        assertThrows(BusinessException.class, () -> service.overview(time, time));
        assertThrows(BusinessException.class, () -> service.revenue(time, time.minusSeconds(1), "day"));
        assertThrows(BusinessException.class, () -> service.revenue(time, time.plusSeconds(60), "year"));
        assertThrows(BusinessException.class, () -> service.spaOccupancy(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 2)));
        assertThrows(BusinessException.class, () -> service.spaOccupancy(LocalDate.MAX, LocalDate.MAX));
    }

    void receipt(int id, String created, int amount, String gateway, String direction, String status) {
        jdbc.update("INSERT INTO payment_transactions VALUES (?,CAST(? AS DATETIME),?,?,?,?)", id, created, amount, gateway, direction, status);
    }
    void assertMoney(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual)); }
}
