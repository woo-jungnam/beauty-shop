package com.core.beautyshop.modules.spa.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ServiceBookingOrderQueryTest {
    JdbcTemplate jdbc;
    ServiceBookingOrderQuery query;

    @BeforeEach void schema() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:service_orders_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE users(id BIGINT, full_name VARCHAR(100))");
        jdbc.execute("CREATE TABLE appointments(id BIGINT PRIMARY KEY,user_id BIGINT,appointment_date DATE,start_time TIME,status VARCHAR(30),created_at TIMESTAMP,is_deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE orders(id BIGINT,appointment_id BIGINT,is_deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE beauty_services(id BIGINT,name VARCHAR(100))");
        jdbc.execute("CREATE TABLE appointment_items(id BIGINT,appointment_id BIGINT,service_id BIGINT,service_name_snapshot VARCHAR(100),price DECIMAL(14,2),ticket_id BIGINT,is_deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE spa_financial_entries(appointment_id BIGINT,kind VARCHAR(20),amount DECIMAL(14,2))");
        jdbc.execute("INSERT INTO users VALUES(7,'Customer A'),(8,'Customer B')");
        jdbc.execute("INSERT INTO beauty_services VALUES(1,'Current service name')");
        query = new ServiceBookingOrderQuery(jdbc);
    }

    void booking(int id, int user, Long ticket, boolean deleted) {
        jdbc.update("INSERT INTO appointments VALUES(?,?,'2026-12-01','09:00:00','PENDING','2026-11-07 10:00:00',?)", id, user, deleted);
        jdbc.update("INSERT INTO appointment_items VALUES(?,?,1,'Purchased service name',100,?,false)", id, id, ticket);
    }

    @Test void showsPaidBookingBeforeInvoiceAndUsesSnapshotAndActualReceipts() {
        booking(1, 7, null, false);
        jdbc.execute("INSERT INTO appointment_items VALUES(2,1,1,'Another single service',200,NULL,false),(3,1,1,'Ticket service',900,10,false)");
        jdbc.execute("INSERT INTO spa_financial_entries VALUES(1,'DEPOSIT',100),(1,'DEPOSIT',200),(1,'REFUND',50),(1,'APPLY',100),(1,'FEE',10)");
        var page = query.list(7L, PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        var row = page.getContent().getFirst();
        assertEquals("PENDING", row.status());
        assertEquals(0, new BigDecimal("300").compareTo(row.estimatedAmount()));
        assertEquals(0, new BigDecimal("300").compareTo(row.collectedAmount()));
        assertEquals(0, new BigDecimal("50").compareTo(row.refundedAmount()));
        assertEquals(java.util.List.of("Purchased service name", "Another single service"), row.services());
    }

    @Test void customerScopeAndAdminPaginationExcludeDeletedAndTicketOnlyBookings() {
        booking(1, 7, null, false); booking(2, 8, null, false);
        booking(3, 7, 99L, false); booking(4, 7, null, true);
        assertEquals(1, query.list(7L, PageRequest.of(0, 10)).getTotalElements());
        var admin = query.list(null, PageRequest.of(0, 1));
        assertEquals(2, admin.getTotalElements());
        assertEquals(2L, admin.getContent().getFirst().appointmentId());
        assertEquals(1L, query.list(null, PageRequest.of(1, 1)).getContent().getFirst().appointmentId());
    }

    @Test void disappearsFromBookingSectionOnceInvoiceExistsWithoutHidingOtherBookings() {
        booking(1, 7, null, false); booking(2, 7, null, false);
        assertEquals(2, query.list(7L, PageRequest.of(0, 10)).getTotalElements());
        jdbc.execute("INSERT INTO orders VALUES(100,1,false)");
        var page = query.list(7L, PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        assertEquals(2L, page.getContent().getFirst().appointmentId());
    }
}
