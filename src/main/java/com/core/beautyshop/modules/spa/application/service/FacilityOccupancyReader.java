package com.core.beautyshop.modules.spa.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;

/** Ordinary reads run under the facility row mutex; no appointment JOIN locks. */
@Component @RequiredArgsConstructor
public class FacilityOccupancyReader {
    private final JdbcTemplate jdbc;
    private static final String LIVE = " ai.is_deleted = false and a.is_deleted = false "
            + "and a.status in ('PENDING','CONFIRMED','IN_PROGRESS') "
            + "and coalesce(ai.execution_status,'PLANNED') not in ('SKIPPED','NO_SHOW') "
            + "and ai.start_time is not null and ai.end_time is not null ";

    public List<Occupancy> outstanding(Long facilityId, Long excludedAppointmentId) {
        String exclusion = " and (? is null or a.id <> ?) ";
        String sql = "select a.appointment_date, ai.start_time, ai.end_time, fa.quantity "
                + "from facility_allocations fa join appointment_items ai on ai.id = fa.appointment_item_id "
                + "join appointments a on a.id = ai.appointment_id where fa.is_deleted = false and fa.facility_id = ? and "
                + LIVE + exclusion
                + "union all select a.appointment_date, ai.start_time, ai.end_time, 1 as quantity "
                + "from appointment_items ai join appointments a on a.id = ai.appointment_id "
                + "where ai.facility_id = ? and " + LIVE + exclusion
                + "and not exists (select 1 from facility_allocations fa where fa.appointment_item_id = ai.id and fa.is_deleted = false)";
        // Match Hibernate's UTC JDBC calendar for SQL TIME. Using the JVM default calendar
        // shifts LocalTime values when the application server runs outside UTC.
        Calendar jdbcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        return jdbc.query(sql, (rs, row) -> new Occupancy(facilityId, rs.getDate("appointment_date").toLocalDate(),
                rs.getTime("start_time", jdbcCalendar).toLocalTime(), rs.getTime("end_time", jdbcCalendar).toLocalTime(), rs.getInt("quantity")),
                facilityId, excludedAppointmentId, excludedAppointmentId, facilityId, excludedAppointmentId, excludedAppointmentId);
    }

    /** Actual occupation stays active until execution ends, regardless of the planned slot. */
    public long executingUnits(Long facilityId, Long excludedItemId) {
        String live = " ai.is_deleted = false and a.is_deleted = false and a.status = 'IN_PROGRESS' "
                + "and ai.execution_status = 'IN_PROGRESS' and (? is null or ai.id <> ?) ";
        String sql = "select coalesce(sum(occupied.quantity), 0) from ("
                + "select fa.quantity from facility_allocations fa join appointment_items ai on ai.id = fa.appointment_item_id "
                + "join appointments a on a.id = ai.appointment_id where fa.is_deleted = false and fa.facility_id = ? and " + live
                + "union all select 1 as quantity from appointment_items ai join appointments a on a.id = ai.appointment_id "
                + "where ai.facility_id = ? and " + live
                + "and not exists (select 1 from facility_allocations fa where fa.appointment_item_id = ai.id and fa.is_deleted = false)"
                + ") occupied";
        Long units = jdbc.queryForObject(sql, Long.class, facilityId, excludedItemId, excludedItemId,
                facilityId, excludedItemId, excludedItemId);
        return units == null ? 0 : units;
    }

    public boolean hasOutstanding(Long facilityId) { return !outstanding(facilityId, null).isEmpty(); }

    public boolean hasOutstandingDuring(Long facilityId, LocalDateTime start, LocalDateTime end) {
        return outstanding(facilityId, null).stream().anyMatch(value ->
                value.date().atTime(value.start()).isBefore(end) && value.date().atTime(value.end()).isAfter(start));
    }

    public record Occupancy(Long facilityId, LocalDate date, LocalTime start, LocalTime end, int quantity) { }
}
