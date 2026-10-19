package com.core.beautyshop.modules.spa.api;
import com.core.beautyshop.modules.spa.application.service.SpaBusinessCalendarService;
import com.core.beautyshop.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController @RequiredArgsConstructor
public class SpaCalendarController {
    private final SpaBusinessCalendarService calendar;
    @GetMapping("/api/v1/spa/business-calendar")
    public ApiResponse<SpaBusinessCalendarService.Window> date(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date) { return ApiResponse.success(calendar.forDate(date)); }
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')") @GetMapping("/api/v1/admin/spa/calendar/weekly")
    public ApiResponse<List<SpaBusinessCalendarService.Hours>> weekly() { return ApiResponse.success(calendar.weekly()); }
    @PreAuthorize("hasRole('ADMIN')") @PutMapping("/api/v1/admin/spa/calendar/weekly")
    public ApiResponse<List<SpaBusinessCalendarService.Hours>> weekly(@RequestBody List<SpaBusinessCalendarService.Hours> rows) { return ApiResponse.success(calendar.saveWeekly(rows)); }
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')") @GetMapping("/api/v1/admin/spa/calendar/exceptions")
    public ApiResponse<List<SpaBusinessCalendarService.Window>> exceptions(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) { return ApiResponse.success(calendar.exceptions(from,to)); }
    @PreAuthorize("hasRole('ADMIN')") @PutMapping("/api/v1/admin/spa/calendar/exceptions/{date}")
    public ApiResponse<SpaBusinessCalendarService.Window> save(@PathVariable @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,@RequestBody SpaBusinessCalendarService.ExceptionCommand c) { return ApiResponse.success(calendar.saveException(date,c)); }
    @PreAuthorize("hasRole('ADMIN')") @DeleteMapping("/api/v1/admin/spa/calendar/exceptions/{date}")
    public ApiResponse<Void> delete(@PathVariable @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date) { calendar.deleteException(date); return ApiResponse.success(null); }
}
