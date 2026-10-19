package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Date;
import java.sql.Time;
import java.time.*;
import java.util.List;

/** One store calendar. Calendar changes and new reservations share the same row mutex. */
@Service @RequiredArgsConstructor
public class SpaBusinessCalendarService {
    private final JdbcTemplate jdbc;
    public record Window(LocalDate date, boolean closed, LocalTime opensAt, LocalTime closesAt, String reason) {}
    public record Hours(int dayOfWeek, boolean closed, LocalTime opensAt, LocalTime closesAt) {}
    public record ExceptionCommand(boolean closed, LocalTime opensAt, LocalTime closesAt, String reason) {}

    public void lock() { jdbc.queryForObject("SELECT id FROM spa_calendar_guard WHERE id=1 FOR UPDATE", Long.class); }
    @Transactional(readOnly = true)
    public Window forDate(LocalDate date) {
        if (date == null) throw new BusinessException("Ngày hoạt động là bắt buộc");
        List<Window> exceptions = jdbc.query("SELECT * FROM spa_calendar_exceptions WHERE work_date=?", (rs,n) ->
                new Window(date, rs.getBoolean("closed"), time(rs.getTime("opens_at")), time(rs.getTime("closes_at")), rs.getString("reason")), Date.valueOf(date));
        if (!exceptions.isEmpty()) return exceptions.getFirst();
        return jdbc.queryForObject("SELECT * FROM spa_weekly_hours WHERE day_of_week=?", (rs,n) ->
                new Window(date, rs.getBoolean("closed"), time(rs.getTime("opens_at")), time(rs.getTime("closes_at")), null), date.getDayOfWeek().getValue());
    }
    public void requireOpen(LocalDate date, LocalTime start, LocalTime end) { requireFits(forDate(date), start, end); }
    public static void requireFits(Window window, LocalTime start, LocalTime end) {
        if (window.closed() || start == null || end == null || !end.isAfter(start)
                || start.isBefore(window.opensAt()) || end.isAfter(window.closesAt()))
            throw new BusinessException(window.closed() ? "Spa nghỉ trong ngày đã chọn" : "Lịch hẹn nằm ngoài giờ hoạt động của Spa");
    }
    @Transactional(readOnly = true)
    public List<Hours> weekly() { return jdbc.query("SELECT * FROM spa_weekly_hours ORDER BY day_of_week", (rs,n) ->
            new Hours(rs.getInt("day_of_week"), rs.getBoolean("closed"), time(rs.getTime("opens_at")), time(rs.getTime("closes_at")))); }
    @Transactional
    public List<Hours> saveWeekly(List<Hours> rows) {
        if (rows == null || rows.size()!=7 || rows.stream().map(Hours::dayOfWeek).distinct().count()!=7)
            throw new BusinessException("Cần cấu hình đủ bảy ngày trong tuần");
        lock();
        for (Hours row: rows) {
            if (row.dayOfWeek()<1 || row.dayOfWeek()>7) throw new BusinessException("Thứ trong tuần không hợp lệ");
            validateHours(row.closed(), row.opensAt(), row.closesAt());
            jdbc.update("UPDATE spa_weekly_hours SET closed=?,opens_at=?,closes_at=? WHERE day_of_week=?", row.closed(), Time.valueOf(row.opensAt()), Time.valueOf(row.closesAt()), row.dayOfWeek());
        }
        validateExisting(); return weekly();
    }
    @Transactional(readOnly = true)
    public List<Window> exceptions(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT * FROM spa_calendar_exceptions WHERE work_date BETWEEN ? AND ? ORDER BY work_date", (rs,n) ->
                new Window(rs.getDate("work_date").toLocalDate(),rs.getBoolean("closed"),time(rs.getTime("opens_at")),time(rs.getTime("closes_at")),rs.getString("reason")), Date.valueOf(from),Date.valueOf(to));
    }
    @Transactional
    public Window saveException(LocalDate date, ExceptionCommand command) {
        if (date == null || date.isBefore(LocalDate.now(SpaTimeRules.ZONE)) || command==null || command.reason()==null || command.reason().isBlank() || command.reason().length()>500)
            throw new BusinessException("Ngày hiện tại/tương lai và lý do thay đổi tối đa 500 ký tự là bắt buộc");
        validateHours(command.closed(), command.opensAt(), command.closesAt()); lock();
        jdbc.update("DELETE FROM spa_calendar_exceptions WHERE work_date=?",Date.valueOf(date));
        jdbc.update("INSERT INTO spa_calendar_exceptions(work_date,closed,opens_at,closes_at,reason,updated_by) VALUES (?,?,?,?,?,?)",Date.valueOf(date),command.closed(), Time.valueOf(command.opensAt()),Time.valueOf(command.closesAt()),command.reason().trim(),SecurityUtils.getCurrentUserId());
        validateExisting(); return forDate(date);
    }
    @Transactional
    public void deleteException(LocalDate date) { lock(); jdbc.update("DELETE FROM spa_calendar_exceptions WHERE work_date=?",Date.valueOf(date)); validateExisting(); }
    private void validateExisting() {
        jdbc.query("SELECT appointment_date,start_time,end_time FROM appointments WHERE is_deleted=FALSE AND appointment_date>=? AND status IN ('PENDING','CONFIRMED','IN_PROGRESS')", rs -> {
            requireOpen(rs.getDate("appointment_date").toLocalDate(),rs.getTime("start_time").toLocalTime(),rs.getTime("end_time").toLocalTime());
        },Date.valueOf(LocalDate.now(SpaTimeRules.ZONE)));
    }
    private static void validateHours(boolean closed,LocalTime opens,LocalTime closes) {
        if (opens==null || closes==null || !closes.isAfter(opens)) throw new BusinessException("Giờ mở và đóng hợp lệ là bắt buộc, kể cả ngày nghỉ");
    }
    private static LocalTime time(Time value) { return value==null ? null : value.toLocalTime(); }
}
