package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.identity.api.*;
import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.enums.StaffScheduleStatus;
import com.core.beautyshop.shared.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminStaffService {
    private final StaffRepository staffRepository;
    private final StaffServiceSkillRepository skillRepository;
    private final StaffScheduleRepository scheduleRepository;
    private final BeautyServiceRepository beautyServiceRepository;
    private final IdentityFacade identityFacade;

    @Transactional(readOnly = true)
    public List<StaffView> list() {
        List<Staff> rows = staffRepository.findAllAdminWithSkills();
        Map<Long, com.core.beautyshop.modules.identity.api.dto.UserSummaryDto> users = identityFacade.findUserSummaries(rows.stream().map(Staff::getUserId).toList());
        return rows.stream().map(row -> StaffView.from(row, users.get(row.getUserId()))).toList();
    }
    @Transactional
    public StaffView save(Long id, StaffCommand command) {
        if (!identityFacade.existsById(command.userId())) throw new ResourceNotFoundException("User not found");
        Staff staff = id == null ? new Staff() : findStaff(id);
        staffRepository.findByUserId(command.userId()).filter(found -> !Objects.equals(found.getId(), id))
                .ifPresent(found -> { throw new BusinessException("User already has a staff profile"); });
        staff.setUserId(command.userId()); staff.setSpecialty(command.specialty()); staff.setBio(command.bio());
        staff.setIsActive(command.active() == null || command.active());
        Staff saved = staffRepository.save(staff);
        return StaffView.from(saved, identityFacade.getUserSummaryById(saved.getUserId()));
    }
    @Transactional
    public void delete(Long id) { Staff staff = findStaff(id); staff.setIsDeleted(true); staff.setIsActive(false); }
    @Transactional
    public StaffView assignSkill(Long staffId, SkillCommand command) {
        Staff staff = findStaff(staffId);
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
    @Transactional
    public void removeSkill(Long staffId, Long serviceId) {
        StaffServiceSkill skill = skillRepository.findByStaffIdAndServiceIdAndIsDeletedFalse(staffId, serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff skill not found"));
        skill.setIsDeleted(true);
    }
    @Transactional(readOnly = true)
    public Page<ScheduleView> schedules(Long staffId, LocalDate from, LocalDate to, Pageable pageable) {
        findStaff(staffId);
        LocalDate start = from == null ? LocalDate.now().minusMonths(1) : from;
        LocalDate end = to == null ? LocalDate.now().plusMonths(3) : to;
        return scheduleRepository.findByStaffIdAndWorkDateBetween(staffId, start, end, pageable).map(ScheduleView::from);
    }
    @Transactional
    public ScheduleView saveSchedule(Long staffId, Long scheduleId, ScheduleCommand command) {
        if (command == null || command.workDate() == null || command.startTime() == null || command.endTime() == null
                || !command.endTime().isAfter(command.startTime())) throw new BusinessException("Invalid shift date or time");
        Staff staff = findStaff(staffId);
        boolean overlap = scheduleId == null
                ? scheduleRepository.existsOverlap(staffId, command.workDate(), command.startTime(), command.endTime())
                : scheduleRepository.existsOverlapExcluding(staffId, scheduleId, command.workDate(), command.startTime(), command.endTime());
        if (overlap) throw new BusinessException("Staff schedule overlaps an existing shift");
        StaffSchedule schedule = scheduleId == null ? new StaffSchedule() : scheduleRepository.findById(scheduleId)
                .filter(value -> Objects.equals(value.getStaff().getId(), staffId) && !Boolean.TRUE.equals(value.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found"));
        schedule.setStaff(staff); schedule.setWorkDate(command.workDate()); schedule.setStartTime(command.startTime()); schedule.setEndTime(command.endTime());
        schedule.setStatus(command.status() == null ? StaffScheduleStatus.SCHEDULED : command.status()); schedule.setNote(command.note());
        return ScheduleView.from(scheduleRepository.save(schedule));
    }
    @Transactional
    public void deleteSchedule(Long staffId, Long id) {
        StaffSchedule schedule = scheduleRepository.findById(id).filter(value -> Objects.equals(value.getStaff().getId(), staffId))
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found"));
        schedule.setIsDeleted(true); schedule.setStatus(StaffScheduleStatus.CANCELLED);
    }
    private Staff findStaff(Long id) { return staffRepository.findById(id).filter(value -> !Boolean.TRUE.equals(value.getIsDeleted()))
            .orElseThrow(() -> new ResourceNotFoundException("Staff not found: " + id)); }

    public record StaffCommand(Long userId, String specialty, String bio, Boolean active) { }
    public record SkillCommand(Long serviceId, Boolean certified) { }
    public record SkillView(Long serviceId, String serviceName, Boolean certified) { }
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
    public record ScheduleCommand(LocalDate workDate, LocalTime startTime, LocalTime endTime, StaffScheduleStatus status, String note) { }
    public record ScheduleView(Long id, Long staffId, LocalDate workDate, LocalTime startTime, LocalTime endTime, StaffScheduleStatus status, String note) {
        static ScheduleView from(StaffSchedule schedule) { return new ScheduleView(schedule.getId(), schedule.getStaff().getId(), schedule.getWorkDate(), schedule.getStartTime(), schedule.getEndTime(), schedule.getStatus(), schedule.getNote()); }
    }
}
