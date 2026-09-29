package com.core.beautyshop.modules.dashboard.application;

import com.core.beautyshop.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Overview overview(Instant from, Instant to) {
        TimeRange range = range(from, to);
        Map<String, Object> order = jdbc.queryForMap("""
                SELECT COUNT(*) total_orders,
                       COALESCE(SUM(CASE WHEN payment_status = 'PAID' THEN paid_amount ELSE 0 END), 0) revenue,
                       COALESCE(SUM(CASE WHEN status IN ('PENDING','CONFIRMED','PROCESSING') THEN 1 ELSE 0 END), 0) open_orders,
                       COALESCE(AVG(CASE WHEN payment_status = 'PAID' THEN total_amount END), 0) average_order_value
                FROM orders WHERE is_deleted = false AND created_at >= ? AND created_at < ?
                """, Timestamp.from(range.from()), Timestamp.from(range.to()));
        Long customers = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE is_deleted = false AND status = 'ACTIVE'", Long.class);
        Long lowStock = jdbc.queryForObject("SELECT COUNT(*) FROM warehouse_stocks WHERE is_deleted = false AND (quantity - reserved_quantity) <= min_quantity", Long.class);
        BigDecimal inventory = jdbc.queryForObject("SELECT COALESCE(SUM(quantity * cost_price), 0) FROM warehouse_stocks WHERE is_deleted = false", BigDecimal.class);
        return new Overview(number(order.get("total_orders")).longValue(), decimal(order.get("revenue")),
                number(order.get("open_orders")).longValue(), decimal(order.get("average_order_value")),
                customers == null ? 0 : customers, lowStock == null ? 0 : lowStock, inventory == null ? BigDecimal.ZERO : inventory);
    }

    @Transactional(readOnly = true)
    public List<RevenuePoint> revenue(Instant from, Instant to, String period) {
        TimeRange range = range(from, to);
        String format = switch (period == null ? "day" : period.toLowerCase(Locale.ROOT)) {
            case "day" -> "%Y-%m-%d";
            case "month" -> "%Y-%m";
            default -> throw new BusinessException("Period must be day or month");
        };
        return jdbc.query("""
                SELECT DATE_FORMAT(created_at, ?) period_key,
                       COALESCE(SUM(paid_amount), 0) revenue, COUNT(*) order_count
                FROM orders
                WHERE is_deleted = false AND payment_status = 'PAID' AND created_at >= ? AND created_at < ?
                GROUP BY DATE_FORMAT(created_at, ?) ORDER BY period_key
                """, (rs, row) -> new RevenuePoint(rs.getString("period_key"), rs.getBigDecimal("revenue"), rs.getLong("order_count")),
                format, Timestamp.from(range.from()), Timestamp.from(range.to()), format);
    }

    @Transactional(readOnly = true)
    public List<TopProduct> topProducts(Instant from, Instant to) {
        TimeRange range = range(from, to);
        return jdbc.query("""
                SELECT p.id product_id, p.name product_name, SUM(oi.quantity) units_sold,
                       SUM((oi.price - oi.discount) * oi.quantity) gross_sales
                FROM orders o JOIN order_items oi ON oi.order_id = o.id
                JOIN product_variants pv ON pv.id = oi.product_variant_id
                JOIN products p ON p.id = pv.product_id
                WHERE o.is_deleted = false AND oi.is_deleted = false AND o.status = 'DELIVERED'
                  AND o.created_at >= ? AND o.created_at < ?
                GROUP BY p.id, p.name ORDER BY units_sold DESC, gross_sales DESC LIMIT 10
                """, (rs, row) -> new TopProduct(rs.getLong("product_id"), rs.getString("product_name"),
                rs.getLong("units_sold"), rs.getBigDecimal("gross_sales")), Timestamp.from(range.from()), Timestamp.from(range.to()));
    }

    @Transactional(readOnly = true)
    public SpaOccupancy spaOccupancy(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now().plusMonths(1).withDayOfMonth(1) : to.plusDays(1);
        Number scheduled = jdbc.queryForObject("""
                SELECT COALESCE(SUM(TIMESTAMPDIFF(MINUTE, start_time, end_time)), 0)
                FROM staff_schedules WHERE is_deleted = false AND status = 'SCHEDULED' AND work_date >= ? AND work_date < ?
                """, Number.class, start, end);
        Number booked = jdbc.queryForObject("""
                SELECT COALESCE(SUM(TIMESTAMPDIFF(MINUTE, ai.start_time, ai.end_time)), 0)
                FROM appointment_items ai JOIN appointments a ON a.id = ai.appointment_id
                WHERE ai.is_deleted = false AND a.is_deleted = false AND a.status <> 'CANCELLED'
                  AND a.appointment_date >= ? AND a.appointment_date < ?
                """, Number.class, start, end);
        long scheduledMinutes = scheduled == null ? 0 : scheduled.longValue();
        long bookedMinutes = booked == null ? 0 : booked.longValue();
        BigDecimal rate = scheduledMinutes == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(bookedMinutes)
                .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(scheduledMinutes), 2, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(100));
        return new SpaOccupancy(scheduledMinutes, bookedMinutes, rate);
    }

    private TimeRange range(Instant from, Instant to) {
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minus(Duration.ofDays(30)) : from;
        if (!end.isAfter(start)) throw new BusinessException("Invalid dashboard time range");
        return new TimeRange(start, end);
    }
    private Number number(Object value) { return value instanceof Number n ? n : 0; }
    private BigDecimal decimal(Object value) { return value instanceof BigDecimal b ? b : value instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : BigDecimal.ZERO; }
    private record TimeRange(Instant from, Instant to) { }
    public record Overview(long totalOrders, BigDecimal revenue, long openOrders, BigDecimal averageOrderValue,
                           long activeCustomers, long lowStockItems, BigDecimal inventoryValue) { }
    public record RevenuePoint(String period, BigDecimal revenue, long orderCount) { }
    public record TopProduct(Long productId, String productName, long unitsSold, BigDecimal grossSales) { }
    public record SpaOccupancy(long scheduledMinutes, long bookedMinutes, BigDecimal occupancyRatePercent) { }
}
