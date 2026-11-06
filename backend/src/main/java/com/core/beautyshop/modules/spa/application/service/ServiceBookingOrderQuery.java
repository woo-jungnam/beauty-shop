package com.core.beautyshop.modules.spa.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Read-only order-page view of booked, individually charged services awaiting an invoice. */
@Service
@RequiredArgsConstructor
public class ServiceBookingOrderQuery {
    private final JdbcTemplate jdbc;

    public record Booking(Long appointmentId, String customerName, LocalDate appointmentDate, LocalTime startTime,
                          String status, BigDecimal estimatedAmount, BigDecimal collectedAmount,
                          BigDecimal refundedAmount, List<String> services) {}

    @Transactional(readOnly = true)
    public Page<Booking> list(Long userId, Pageable pageable) {
        String scope = """
                FROM appointments a
                WHERE a.is_deleted = false AND (? IS NULL OR a.user_id = ?)
                  AND EXISTS (SELECT 1 FROM appointment_items ai WHERE ai.appointment_id = a.id
                      AND ai.is_deleted = false AND ai.ticket_id IS NULL)
                  AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.appointment_id = a.id AND o.is_deleted = false)
                """;
        Long count = jdbc.queryForObject("SELECT COUNT(*) " + scope, Long.class, userId, userId);
        List<Booking> content = jdbc.query("""
                SELECT a.id, a.appointment_date, a.start_time, a.status,
                       (SELECT u.full_name FROM users u WHERE u.id = a.user_id) customer_name,
                       (SELECT COALESCE(SUM(ai.price),0) FROM appointment_items ai
                        WHERE ai.appointment_id = a.id AND ai.is_deleted = false AND ai.ticket_id IS NULL) estimated_amount,
                       (SELECT COALESCE(SUM(f.amount),0) FROM spa_financial_entries f
                        WHERE f.appointment_id = a.id AND f.kind = 'DEPOSIT') collected_amount,
                       (SELECT COALESCE(SUM(f.amount),0) FROM spa_financial_entries f
                        WHERE f.appointment_id = a.id AND f.kind = 'REFUND') refunded_amount
                """ + scope + " ORDER BY a.created_at DESC, a.id DESC LIMIT ? OFFSET ?", (rs, index) -> {
            long id = rs.getLong("id");
            List<String> names = jdbc.queryForList("""
                    SELECT COALESCE(ai.service_name_snapshot, s.name) FROM appointment_items ai
                    JOIN beauty_services s ON s.id = ai.service_id
                    WHERE ai.appointment_id = ? AND ai.is_deleted = false AND ai.ticket_id IS NULL ORDER BY ai.id
                    """, String.class, id);
            return new Booking(id, rs.getString("customer_name"), rs.getObject("appointment_date", LocalDate.class),
                    rs.getObject("start_time", LocalTime.class), rs.getString("status"), rs.getBigDecimal("estimated_amount"),
                    rs.getBigDecimal("collected_amount"), rs.getBigDecimal("refunded_amount"), names);
        }, userId, userId, pageable.getPageSize(), pageable.getOffset());
        return new PageImpl<>(content, pageable, count == null ? 0 : count);
    }
}
