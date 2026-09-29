package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.modules.spa.application.service.AdminStaffService;
import com.core.beautyshop.shared.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/staff")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminStaffController {
    private final AdminStaffService service;
    @GetMapping public ApiResponse<List<AdminStaffService.StaffView>> list() { return ApiResponse.success(service.list()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ApiResponse<AdminStaffService.StaffView> create(@RequestBody AdminStaffService.StaffCommand command) { return ApiResponse.created(service.save(null, command), "Staff created"); }
    @PutMapping("/{id}") public ApiResponse<AdminStaffService.StaffView> update(@PathVariable Long id, @RequestBody AdminStaffService.StaffCommand command) { return ApiResponse.success(service.save(id, command)); }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.success(null); }
    @PutMapping("/{id}/skills") public ApiResponse<AdminStaffService.StaffView> skill(@PathVariable Long id, @RequestBody AdminStaffService.SkillCommand command) { return ApiResponse.success(service.assignSkill(id, command)); }
    @DeleteMapping("/{id}/skills/{serviceId}") public ApiResponse<Void> removeSkill(@PathVariable Long id, @PathVariable Long serviceId) { service.removeSkill(id, serviceId); return ApiResponse.success(null); }
    @GetMapping("/{id}/schedules") public ApiResponse<PageResponse<AdminStaffService.ScheduleView>> schedules(@PathVariable Long id, @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, Pageable pageable) { return ApiResponse.success(PageResponse.of(service.schedules(id, from, to, pageable))); }
    @PostMapping("/{id}/schedules") @ResponseStatus(HttpStatus.CREATED) public ApiResponse<AdminStaffService.ScheduleView> createSchedule(@PathVariable Long id, @RequestBody AdminStaffService.ScheduleCommand command) { return ApiResponse.created(service.saveSchedule(id, null, command), "Schedule created"); }
    @PutMapping("/{id}/schedules/{scheduleId}") public ApiResponse<AdminStaffService.ScheduleView> updateSchedule(@PathVariable Long id, @PathVariable Long scheduleId, @RequestBody AdminStaffService.ScheduleCommand command) { return ApiResponse.success(service.saveSchedule(id, scheduleId, command)); }
    @DeleteMapping("/{id}/schedules/{scheduleId}") public ApiResponse<Void> deleteSchedule(@PathVariable Long id, @PathVariable Long scheduleId) { service.deleteSchedule(id, scheduleId); return ApiResponse.success(null); }
}
