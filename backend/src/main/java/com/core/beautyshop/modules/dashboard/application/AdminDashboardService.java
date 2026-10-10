package com.core.beautyshop.modules.dashboard.application;

import com.core.beautyshop.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ColumnMapRowMapper;
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
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String RECEIPTS = "transfer_type = 'in' AND amount > 0 AND status IN ('SUCCESS','PARTIALLY_PAID','CANCELLED_ORDER_RECEIVED','ORDER_NOT_FOUND','IGNORED')";
    private final JdbcTemplate jdbc;

    /** Order volumes use creation time; collections/refunds use their own ledger times; settled orders use paid_at. */
    @Transactional(readOnly = true)
    public Overview overview(Instant from, Instant to) {
        TimeRange range = range(from, to);
        Map<String, Object> order = row("""
                SELECT COUNT(*) total_orders,
                       COALESCE(SUM(CASE WHEN payment_status = 'PENDING' THEN 1 ELSE 0 END), 0) pending_payment_orders,
                       COALESCE(SUM(CASE WHEN status IN ('PENDING','CONFIRMED','PROCESSING') THEN 1 ELSE 0 END), 0) open_orders
                FROM orders WHERE is_deleted = false AND created_at >= ? AND created_at < ?
                """, range.from(), range.to());
        Map<String, Object> settled = row("""
                SELECT COUNT(*) paid_orders, COALESCE(AVG(total_amount), 0) average_order_value
                FROM orders WHERE is_deleted = false AND paid_at >= ? AND paid_at < ?
                """, range.from(), range.to());
        Map<String, Object> collections = row("SELECT COALESCE(SUM(amount),0) revenue, "
                + "COALESCE(SUM(CASE WHEN gateway = 'COD' THEN amount ELSE 0 END),0) cod_amount, COALESCE(SUM(CASE WHEN gateway = 'CASH' THEN amount ELSE 0 END),0) cash_amount, COUNT(*) receipt_count "
                + "FROM payment_transactions WHERE " + RECEIPTS + " AND created_at >= ? AND created_at < ?", range.from(), range.to());
        BigDecimal refunds = decimal(first(row("SELECT COALESCE(SUM(amount),0) refunded_amount FROM refund_confirmations WHERE confirmed_at >= ? AND confirmed_at < ?", range.from(), range.to())));
        long customers = number(first(row("""
                SELECT COUNT(DISTINCT u.id) customer_count FROM users u JOIN user_roles ur ON ur.user_id = u.id
                JOIN roles r ON r.id = ur.role_id WHERE u.is_deleted = false AND u.status = 'ACTIVE'
                AND r.is_deleted = false AND r.name IN ('ROLE_USER', 'ROLE_CUSTOMER')
                """))).longValue();
        // A minimum repeated on several batches must not multiply the SKU threshold.
        long lowStock = number(first(row("""
                SELECT COUNT(*) low_stock_items FROM (
                    SELECT product_variant_id FROM warehouse_stocks WHERE is_deleted = false
                    GROUP BY product_variant_id
                    HAVING SUM(quantity - reserved_quantity) <= MAX(COALESCE(min_quantity,0))
                ) sku_stock
                """))).longValue();
        BigDecimal inventory = decimal(first(row("SELECT COALESCE(SUM(quantity * cost_price),0) inventory_value FROM warehouse_stocks WHERE is_deleted = false")));
        BigDecimal received = decimal(collections.get("revenue"));
        BigDecimal cod = decimal(collections.get("cod_amount"));
        BigDecimal cash = decimal(collections.get("cash_amount"));
        return new Overview(number(order.get("total_orders")).longValue(), received,
                number(settled.get("paid_orders")).longValue(), number(order.get("pending_payment_orders")).longValue(),
                number(order.get("open_orders")).longValue(), decimal(settled.get("average_order_value")),
                customers, lowStock, inventory, refunds, cod, received.subtract(cod).subtract(cash), cash, number(collections.get("receipt_count")).longValue());
    }

    /** Legacy revenue is gross incoming cash, including unmatched receipts and COD. Refunds are shown separately. */
    @Transactional(readOnly = true)
    public List<RevenuePoint> revenue(Instant from, Instant to, String period) {
        TimeRange range = range(from, to);
        String format = switch (period == null ? "day" : period.toLowerCase(Locale.ROOT)) {
            case "day" -> "%Y-%m-%d";
            case "month" -> "%Y-%m";
            default -> throw new BusinessException("Period must be day or month");
        };
        SortedMap<String, RevenueBucket> buckets = new TreeMap<>();
        for (var receipt : rows("""
                SELECT DATE_FORMAT(CONVERT_TZ(created_at,'+00:00','+07:00'), ?) period_key,
                       COALESCE(SUM(amount),0) revenue, COUNT(*) receipt_count,
                       COALESCE(SUM(CASE WHEN gateway = 'COD' THEN amount ELSE 0 END),0) cod_amount, COALESCE(SUM(CASE WHEN gateway = 'CASH' THEN amount ELSE 0 END),0) cash_amount
                FROM payment_transactions WHERE %s AND created_at >= ? AND created_at < ?
                GROUP BY period_key
                """.formatted(RECEIPTS), format, range.from(), range.to())) {
            RevenueBucket bucket = bucket(buckets, receipt);
            bucket.revenue = decimal(receipt.get("revenue"));
            bucket.cod = decimal(receipt.get("cod_amount"));
            bucket.cash = decimal(receipt.get("cash_amount"));
            bucket.receipts = number(receipt.get("receipt_count")).longValue();
        }
        for (var refund : rows("""
                SELECT DATE_FORMAT(CONVERT_TZ(confirmed_at,'+00:00','+07:00'), ?) period_key, COALESCE(SUM(amount),0) refunded_amount
                FROM refund_confirmations WHERE confirmed_at >= ? AND confirmed_at < ?
                GROUP BY period_key
                """, format, range.from(), range.to())) bucket(buckets, refund).refunds = decimal(refund.get("refunded_amount"));
        for (var paid : rows("""
                SELECT DATE_FORMAT(CONVERT_TZ(paid_at,'+00:00','+07:00'), ?) period_key, COUNT(*) order_count
                FROM orders WHERE is_deleted = false AND paid_at >= ? AND paid_at < ?
                GROUP BY period_key
                """, format, range.from(), range.to())) bucket(buckets, paid).orders = number(paid.get("order_count")).longValue();
        return buckets.entrySet().stream().map(entry -> {
            RevenueBucket value = entry.getValue();
            return new RevenuePoint(entry.getKey(), value.revenue, value.orders, value.refunds, value.receipts, value.cod, value.revenue.subtract(value.cod).subtract(value.cash), value.cash);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<TopProduct> topProducts(Instant from, Instant to) {
        TimeRange range = range(from, to);
        return rows("""
                SELECT p.id product_id, p.name product_name, SUM(oi.quantity) units_sold,
                       SUM((oi.price - oi.discount) * oi.quantity) gross_sales
                FROM orders o JOIN order_items oi ON oi.order_id = o.id
                JOIN product_variants pv ON pv.id = oi.product_variant_id JOIN products p ON p.id = pv.product_id
                WHERE o.is_deleted = false AND oi.is_deleted = false AND o.status = 'DELIVERED'
                  AND o.created_at >= ? AND o.created_at < ?
                GROUP BY p.id, p.name ORDER BY units_sold DESC, gross_sales DESC LIMIT 10
                """, range.from(), range.to()).stream().map(value -> new TopProduct(number(value.get("product_id")).longValue(),
                String.valueOf(value.get("product_name")), number(value.get("units_sold")).longValue(), decimal(value.get("gross_sales")))).toList();
    }

    @Transactional(readOnly = true)
    public SpaOccupancy spaOccupancy(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate start = from == null ? today.withDayOfMonth(1) : from;
        LocalDate last = to == null ? today.plusMonths(1).withDayOfMonth(1).minusDays(1) : to;
        if (last.isBefore(start) || last.equals(LocalDate.MAX)) throw new BusinessException("Invalid Spa dashboard date range");
        LocalDate end = last.plusDays(1);
        long scheduled = number(first(row("""
                SELECT COALESCE(SUM(GREATEST(TIMESTAMPDIFF(MINUTE,start_time,end_time),0)),0) scheduled_minutes
                FROM staff_schedules WHERE is_deleted = false AND status = 'SCHEDULED' AND work_date >= ? AND work_date < ?
                """, start, end))).longValue();
        Map<String, Object> appointment = row("""
                SELECT COALESCE(SUM(CASE WHEN a.status IN ('CONFIRMED','IN_PROGRESS','COMPLETED') AND ai.execution_status NOT IN ('SKIPPED','NO_SHOW')
                    THEN GREATEST(TIMESTAMPDIFF(MINUTE,ai.start_time,ai.end_time),0) ELSE 0 END),0) booked_minutes,
                       COALESCE(SUM(CASE WHEN ai.execution_status = 'PERFORMED' AND ai.actual_started_at IS NOT NULL AND ai.actual_completed_at IS NOT NULL
                    THEN GREATEST(TIMESTAMPDIFF(MINUTE,ai.actual_started_at,ai.actual_completed_at),0) ELSE 0 END),0) served_minutes,
                       COALESCE(SUM(CASE WHEN a.status = 'NO_SHOW'
                    THEN GREATEST(TIMESTAMPDIFF(MINUTE,ai.start_time,ai.end_time),0) ELSE 0 END),0) no_show_minutes,
                       COALESCE(SUM(CASE WHEN a.status IN ('PENDING','CONFIRMED','IN_PROGRESS') AND ai.execution_status NOT IN ('SKIPPED','NO_SHOW') THEN GREATEST(TIMESTAMPDIFF(MINUTE,ai.start_time,ai.end_time),0) ELSE 0 END),0) reserved_minutes,
                       COALESCE(SUM(CASE WHEN a.status = 'COMPLETED' AND ai.execution_status = 'LEGACY_FINALIZED' THEN GREATEST(TIMESTAMPDIFF(MINUTE,ai.start_time,ai.end_time),0) ELSE 0 END),0) legacy_finalized_minutes,
                       COUNT(DISTINCT CASE WHEN a.status = 'NO_SHOW' THEN a.id END) no_show_appointments
                FROM appointment_items ai JOIN appointments a ON a.id = ai.appointment_id
                WHERE ai.is_deleted = false AND a.is_deleted = false AND ai.staff_id IS NOT NULL
                  AND ai.start_time IS NOT NULL AND ai.end_time IS NOT NULL
                  AND a.appointment_date >= ? AND a.appointment_date < ?
                """, start, end);
        long booked = number(appointment.get("booked_minutes")).longValue();
        Instant actualFrom = start.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant actualTo = end.atStartOfDay(BUSINESS_ZONE).toInstant();
        long served = number(first(row("""
                SELECT COALESCE(SUM(GREATEST(TIMESTAMPDIFF(MINUTE,
                    GREATEST(ai.actual_started_at,?),LEAST(ai.actual_completed_at,?)),0)),0) actual_minutes
                FROM appointment_items ai JOIN appointments a ON a.id=ai.appointment_id
                WHERE ai.is_deleted=false AND a.is_deleted=false AND ai.staff_id IS NOT NULL
                  AND ai.execution_status='PERFORMED' AND ai.actual_started_at < ? AND ai.actual_completed_at > ?
                """, actualFrom, actualTo, actualTo, actualFrom))).longValue();
        long overload = Math.max(0, booked - scheduled);
        return new SpaOccupancy(scheduled, booked, percentage(booked, scheduled), served,
                number(appointment.get("no_show_minutes")).longValue(), number(appointment.get("no_show_appointments")).longValue(),
                overload, overload > 0, percentage(served, scheduled), number(appointment.get("reserved_minutes")).longValue(), number(appointment.get("legacy_finalized_minutes")).longValue());
    }

    /** Current operational balances, independent of receipt/creation reporting periods. */
    @Transactional(readOnly = true)
    public SpaFinancials spaFinancials() {
        var due = row("""
                SELECT COUNT(*) outstanding_invoices,
                       COALESCE(SUM(total_amount-paid_amount),0) outstanding_amount
                FROM orders WHERE is_deleted=false AND appointment_id IS NOT NULL
                  AND status='COMPLETED' AND payment_status='PENDING' AND total_amount>paid_amount
                """);
        var unbilled = row("""
                SELECT COUNT(*) unbilled_visits, COALESCE(SUM(visit_amount),0) unbilled_amount FROM (
                    SELECT a.id, ROUND(SUM(ai.price),0) visit_amount
                    FROM appointments a JOIN appointment_items ai ON ai.appointment_id=a.id
                    WHERE a.is_deleted=false AND ai.is_deleted=false AND a.status='COMPLETED'
                      AND ai.execution_status='PERFORMED' AND ai.ticket_id IS NULL
                      AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.appointment_id=a.id AND o.is_deleted=false)
                    GROUP BY a.id
                ) completed_visits
                """);
        long legacy = number(first(row("""
                SELECT COUNT(DISTINCT a.id) legacy_visits
                FROM appointments a JOIN appointment_items ai ON ai.appointment_id=a.id
                WHERE a.is_deleted=false AND ai.is_deleted=false AND a.status='COMPLETED'
                  AND ai.execution_status='LEGACY_FINALIZED'
                  AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.appointment_id=a.id AND o.is_deleted=false)
                """))).longValue();
        return new SpaFinancials(number(due.get("outstanding_invoices")).longValue(), decimal(due.get("outstanding_amount")),
                number(unbilled.get("unbilled_visits")).longValue(), decimal(unbilled.get("unbilled_amount")), legacy);
    }

    private TimeRange range(Instant from, Instant to) {
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minus(Duration.ofDays(30)) : from;
        if (!end.isAfter(start)) throw new BusinessException("Invalid dashboard time range");
        return new TimeRange(start, end);
    }

    /** UTC parameter binding is independent of the JVM's default zone. MySQL connections also use UTC. */
    private List<Map<String, Object>> rows(String sql, Object... parameters) {
        return jdbc.query(connection -> {
            var statement = connection.prepareStatement(sql);
            for (int index = 0; index < parameters.length; index++) {
                Object parameter = parameters[index];
                if (parameter instanceof Instant instant) statement.setTimestamp(index + 1, Timestamp.from(instant), Calendar.getInstance(TimeZone.getTimeZone("UTC")));
                else statement.setObject(index + 1, parameter);
            }
            return statement;
        }, new ColumnMapRowMapper());
    }
    private Map<String, Object> row(String sql, Object... parameters) { return rows(sql, parameters).getFirst(); }
    private Object first(Map<String, Object> row) { return row.values().iterator().next(); }
    private Number number(Object value) { return value instanceof Number n ? n : 0; }
    private BigDecimal decimal(Object value) { return value instanceof BigDecimal b ? b : value instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : BigDecimal.ZERO; }
    private BigDecimal percentage(long numerator, long denominator) { return denominator == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP); }
    private RevenueBucket bucket(Map<String, RevenueBucket> buckets, Map<String, Object> row) { return buckets.computeIfAbsent(String.valueOf(row.get("period_key")), ignored -> new RevenueBucket()); }
    private static class RevenueBucket { BigDecimal revenue = BigDecimal.ZERO, refunds = BigDecimal.ZERO, cod = BigDecimal.ZERO, cash = BigDecimal.ZERO; long receipts, orders; }
    private record TimeRange(Instant from, Instant to) { }

    public record Overview(long totalOrders, BigDecimal revenue, long paidOrders, long pendingPaymentOrders,
                           long openOrders, BigDecimal averageOrderValue, long activeCustomers, long lowStockItems, BigDecimal inventoryValue,
                           BigDecimal refundedAmount, BigDecimal codAmount, BigDecimal bankAmount, BigDecimal cashAmount, long receiptCount) { }
    public record RevenuePoint(String period, BigDecimal revenue, long orderCount, BigDecimal refundedAmount,
                               long receiptCount, BigDecimal codAmount, BigDecimal bankAmount, BigDecimal cashAmount) { }
    public record TopProduct(Long productId, String productName, long unitsSold, BigDecimal grossSales) { }
    public record SpaFinancials(long outstandingInvoices, BigDecimal outstandingAmount,
                               long unbilledCompletedVisits, BigDecimal unbilledAmount, long legacyVisitsRequiringReconciliation) { }
    public record SpaOccupancy(long scheduledMinutes, long bookedMinutes, BigDecimal occupancyRatePercent,
                               long servedMinutes, long noShowMinutes, long noShowAppointments, long overCapacityMinutes,
                               boolean overCapacity, BigDecimal servedRatePercent, long reservedMinutes, long legacyFinalizedMinutes) { }
}
