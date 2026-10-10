package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.identity.api.*;
import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.enums.StaffScheduleStatus;
import com.core.beautyshop.shared.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.*;
import java.util.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Service
@RequiredArgsConstructor
public class AdminStaffService {
    private final StaffRepository staffRepository;
    private final StaffServiceSkillRepository skillRepository;
    private final StaffScheduleRepository scheduleRepository;
    private final BeautyServiceRepository beautyServiceRepository;
    private final IdentityFacade identityFacade;
    private final AppointmentRepository appointments;

    @Transactional(readOnly = true)
    public List<StaffView> list() {
        List<Staff> rows = staffRepository.findAllAdminWithSkills();
        Map<Long, com.core.beautyshop.modules.identity.api.dto.UserSummaryDto> users = identityFacade.findUserSummaries(rows.stream().map(Staff::getUserId).toList());
        return rows.stream().map(row -> StaffView.from(row, users.get(row.getUserId()))).toList();
    }
    @Transactional(readOnly = true)
    public StaffView get(Long id) {
        Staff staff = findStaff(id);
        return StaffView.from(staff, identityFacade.getUserSummaryById(staff.getUserId()));
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StaffView save(Long id, StaffCommand command) {
        if (!identityFacade.existsById(command.userId())) throw new ResourceNotFoundException("User not found");
        Staff staff = id == null ? new Staff() : lockStaff(id);
        if (id != null && (Boolean.FALSE.equals(command.active()) || !Objects.equals(command.userId(), staff.getUserId()))) {
            requireNoFutureAppointments(id, null);
        }
        staffRepository.findByUserId(command.userId()).filter(found -> !Objects.equals(found.getId(), id))
                .ifPresent(found -> { throw new BusinessException("User already has a staff profile"); });
        staff.setUserId(command.userId()); staff.setSpecialty(command.specialty()); staff.setBio(command.bio());
        staff.setIsActive(command.active() == null || command.active());
        Staff saved = staffRepository.save(staff);
        return StaffView.from(saved, identityFacade.getUserSummaryById(saved.getUserId()));
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(Long id) {
        Staff staff = lockStaff(id);
        requireNoFutureAppointments(id, null);
        staff.setIsDeleted(true); staff.setIsActive(false);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StaffView assignSkill(Long staffId, SkillCommand command) {
        Staff staff = lockStaff(staffId);
        BeautyService service = beautyServiceRepository.findById(command.serviceId())
                .filter(value -> !Boolean.TRUE.equals(value.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Spa service not found"));
        StaffServiceSkill skill = skillRepository.findByStaffIdAndServiceIdAndIsDeletedFalse(staffId, command.serviceId()).orElseGet(StaffServiceSkill::new);
        skill.setStaff(staff); skill.setService(service); skill.setIsCertified(Boolean.TRUE.equals(command.certified()));
        skillRepository.save(skill);
        if (staff.getSkills() == null) staff.setSkills(new ArrayList<>());
        if (staff.getSkills().stream().noneMatch(value -> Objects.equals(value.getId(), skill.getId()))) staff.getSkills().add(skill);
        return StaffView.from(staff, identityFacade.getUserSummaryById(staff.getUserId()));
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void removeSkill(Long staffId, Long serviceId) {
        lockStaff(staffId);
        requireNoFutureAppointments(staffId, serviceId);
        StaffServiceSkill skill = skillRepository.findByStaffIdAndServiceIdAndIsDeletedFalse(staffId, serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff skill not found"));
        skill.setIsDeleted(true);
    }
    @Transactional(readOnly = true)
    public Page<ScheduleView> schedules(Long staffId, LocalDate from, LocalDate to, Pageable pageable) {
        findStaff(staffId);
        LocalDate start = from == null ? LocalDate.now(SpaTimeRules.ZONE).minusMonths(1) : from;
        LocalDate end = to == null ? LocalDate.now(SpaTimeRules.ZONE).plusMonths(3) : to;
        if (end.isBefore(start)) throw new BusinessException("Shift end date must not precede start date");
        return scheduleRepository.findByStaffIdAndWorkDateBetweenAndIsDeletedFalse(staffId, start, end, pageable).map(ScheduleView::from);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ScheduleView saveSchedule(Long staffId, Long scheduleId, ScheduleCommand command) {
        if (command == null || command.workDate() == null || command.startTime() == null || command.endTime() == null
                || !command.endTime().isAfter(command.startTime())) throw new BusinessException("Invalid shift date or time");
        Staff staff = lockStaff(staffId);
        boolean overlap = scheduleId == null
                ? scheduleRepository.existsOverlap(staffId, command.workDate(), command.startTime(), command.endTime())
                : scheduleRepository.existsOverlapExcluding(staffId, scheduleId, command.workDate(), command.startTime(), command.endTime());
        if (overlap) throw new BusinessException("Staff schedule overlaps an existing shift");
        StaffSchedule schedule = scheduleId == null ? new StaffSchedule() : scheduleRepository.findById(scheduleId)
                .filter(value -> Objects.equals(value.getStaff().getId(), staffId) && !Boolean.TRUE.equals(value.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found"));
        schedule.setStaff(staff); schedule.setWorkDate(command.workDate()); schedule.setStartTime(command.startTime()); schedule.setEndTime(command.endTime());
        schedule.setStatus(command.status() == null ? StaffScheduleStatus.SCHEDULED : command.status()); schedule.setNote(command.note());
        StaffSchedule saved = scheduleRepository.saveAndFlush(schedule);
        requireAppointmentsCovered(staffId);
        return ScheduleView.from(saved);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteSchedule(Long staffId, Long id) {
        lockStaff(staffId);
        StaffSchedule schedule = scheduleRepository.findById(id).filter(value -> Objects.equals(value.getStaff().getId(), staffId))
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found"));
        schedule.setIsDeleted(true); schedule.setStatus(StaffScheduleStatus.CANCELLED);
        scheduleRepository.flush();
        requireAppointmentsCovered(staffId);
    }
    private Staff findStaff(Long id) { return staffRepository.findById(id).filter(value -> !Boolean.TRUE.equals(value.getIsDeleted()))
            .orElseThrow(() -> new ResourceNotFoundException("Staff not found: " + id)); }

    private Staff lockStaff(Long id) { return staffRepository.findByIdWithLock(id)
            .filter(value -> !Boolean.TRUE.equals(value.getIsDeleted()))
            .orElseThrow(() -> new ResourceNotFoundException("Staff not found: " + id)); }

    private void requireNoFutureAppointments(Long staffId, Long serviceId) {
        if (appointments.findFutureAssignedItems(staffId, LocalDate.now(SpaTimeRules.ZONE)).stream()
                .anyMatch(item -> serviceId == null || item.getService().getId().equals(serviceId))) {
            throw new BusinessException("Reassign or cancel outstanding appointments before changing this staff member");
        }
    }

    private void requireAppointmentsCovered(Long staffId) {
        for (AppointmentItem item : appointments.findFutureAssignedItems(staffId, LocalDate.now(SpaTimeRules.ZONE))) {
            LocalDate date = item.getAppointment().getAppointmentDate();
            if (scheduleRepository.hasScheduleOnDate(staffId, date)) {
                if (!scheduleRepository.coversWorkingInterval(staffId, date, item.getStartTime(), item.getEndTime())) {
                    throw new BusinessException("Shift change would leave an outstanding appointment outside working hours");
                }
            } else {
                if (item.getStartTime().isBefore(SpaTimeRules.OPEN) || item.getEndTime().isAfter(SpaTimeRules.CLOSE)) {
                    throw new BusinessException("Shift change would leave an outstanding appointment outside working hours");
                }
            }
        }
    }

    @Schema(name = "SpaStaffCommand", description = "Hồ sơ staff gắn một tài khoản; không tự cấp role hoặc kỹ năng/ca")
    public record StaffCommand(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "User.id đã tồn tại và chưa có staff profile khác", example = "101") Long userId,
            String specialty, String bio, @Schema(description = "Thiếu/null là true cả khi cập nhật", example = "true") Boolean active) { }
    @Schema(name = "SpaStaffSkillCommand", description = "Gán kỹ năng; certified là thông tin ghi nhận, không thay role hoặc lịch ca")
    public record SkillCommand(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Dịch vụ chưa xóa", example = "1") Long serviceId,
            @Schema(description = "Thiếu/null dùng false", example = "true") Boolean certified) { }
    @Schema(name = "SpaStaffSkillView", description = "Kỹ năng dịch vụ còn hiệu lực trên hồ sơ staff")
    public record SkillView(Long serviceId, String serviceName, Boolean certified) { }
    @Schema(name = "SpaStaffView", description = "Hồ sơ quản trị staff, tài khoản và kỹ năng; có thể inactive")
    public record StaffView(Long id, Long userId, String fullName, String specialty, String bio, Double rating, Integer totalReviews,
                            Boolean active, List<SkillView> skills) {
        static StaffView from(Staff staff, com.core.beautyshop.modules.identity.api.dto.UserSummaryDto user) {
            List<SkillView> skills = staff.getSkills() == null ? List.of() : staff.getSkills().stream()
                    .filter(value -> !Boolean.TRUE.equals(value.getIsDeleted()))
                    .map(value -> new SkillView(value.getService().getId(), value.getService().getName(), value.getIsCertified())).toList();
            return new StaffView(staff.getId(), staff.getUserId(), user == null ? null : user.getFullName(), staff.getSpecialty(), staff.getBio(),
                    staff.getRating(), staff.getTotalReviews(), staff.getIsActive(), skills);
        }
    }
    @Schema(name = "SpaStaffScheduleCommand", description = "Ca trong một ngày tại Việt Nam, endTime>startTime; thay đổi không được làm mất ca phủ lịch đang giao")
    public record ScheduleCommand(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "date", example = "2026-10-05") LocalDate workDate,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "time", example = "08:00:00") LocalTime startTime,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "time", example = "20:00:00") LocalTime endTime,
            @Schema(description = "Thiếu/null dùng SCHEDULED; booking chỉ dùng ca SCHEDULED", example = "SCHEDULED") StaffScheduleStatus status, String note) { }
    @Schema(name = "SpaStaffScheduleView", description = "Ca chưa xóa của staff; ngày và giờ tại Việt Nam")
    public record ScheduleView(Long id, Long staffId, LocalDate workDate, LocalTime startTime, LocalTime endTime, StaffScheduleStatus status, String note) {
        static ScheduleView from(StaffSchedule schedule) { return new ScheduleView(schedule.getId(), schedule.getStaff().getId(), schedule.getWorkDate(), schedule.getStartTime(), schedule.getEndTime(), schedule.getStatus(), schedule.getNote()); }
    }
}
