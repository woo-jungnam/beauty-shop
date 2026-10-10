package com.core.beautyshop.modules.spa.application.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;

import com.core.beautyshop.modules.spa.application.dto.response.BeautyServiceResponse;
import com.core.beautyshop.modules.spa.application.service.BeautyServiceService;
import com.core.beautyshop.modules.spa.domain.BeautyServiceRepository;
import com.core.beautyshop.modules.spa.domain.ServicePackageRepository;
import com.core.beautyshop.modules.spa.domain.StaffRepository;
import com.core.beautyshop.modules.spa.domain.AppointmentRepository;
import com.core.beautyshop.modules.spa.domain.StaffScheduleRepository;
import com.core.beautyshop.modules.spa.application.service.SpaTimeRules;
import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.identity.api.dto.UserSummaryDto;
import com.core.beautyshop.modules.spa.application.dto.response.ServicePackageResponse;
import com.core.beautyshop.modules.spa.application.dto.response.StaffResponse;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BeautyServiceServiceImpl implements BeautyServiceService {

    private final BeautyServiceRepository beautyServiceRepository;
    private final ServicePackageRepository servicePackageRepository;
    private final StaffRepository staffRepository;
    private final AppointmentRepository appointmentRepository;
    private final IdentityFacade identityFacade;
    private final StaffScheduleRepository schedules;
    private final com.core.beautyshop.modules.spa.application.service.FacilitySchedulingService facilities;

    @Override
    @Cacheable(value = "spa_services", key = "'all-active'")
    public List<BeautyServiceResponse> getAllActiveServices() {
        return beautyServiceRepository.findAllActiveWithCategory().stream()
                .map(BeautyServiceResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "spa_services", key = "'id:' + #id")
    public BeautyServiceResponse getServiceById(Long id) {
        return beautyServiceRepository.findWithCategoryById(id)
                .map(BeautyServiceResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dịch vụ spa với ID: " + id));
    }

    @Override
    @Cacheable(value = "spa_services", key = "'slug:' + #slug")
    public BeautyServiceResponse getServiceBySlug(String slug) {
        return beautyServiceRepository.findWithCategoryBySlug(slug)
                .map(BeautyServiceResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dịch vụ spa với slug: " + slug));
    }

    @Override
    public List<ServicePackageResponse> getActivePackages() {
        return servicePackageRepository.findAllActiveWithItems().stream()
                .map(ServicePackageResponse::fromEntity)
                .toList();
    }

    @Override
    public List<StaffResponse> getQualifiedStaff(Long serviceId) {
        beautyServiceRepository.findWithCategoryById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
        var list = staffRepository.findQualifiedActiveStaff(serviceId);
        return list.stream()
                .map(staff -> StaffResponse.of(staff,
                        identityFacade.findUserSummaryById(staff.getUserId())
                                .map(UserSummaryDto::getFullName).orElse(null)))
                .toList();
    }

    @Override
    public List<StaffResponse> getAllStaff() {
        return staffRepository.findAllAdminWithSkills().stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()) && !Boolean.TRUE.equals(s.getIsDeleted()))
                .map(staff -> StaffResponse.of(staff,
                        identityFacade.findUserSummaryById(staff.getUserId())
                                .map(UserSummaryDto::getFullName).orElse(null)))
                .toList();
    }

    @Override
    public List<String> getAvailableSlots(Long serviceId, java.time.LocalDate date, Long staffId) {
        var service = beautyServiceRepository.findWithCategoryById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
        if (!Boolean.TRUE.equals(service.getIsActive()) || Boolean.TRUE.equals(service.getIsDeleted())) {
            throw new BusinessException("Service is not available");
        }
        if (date == null || date.isBefore(java.time.LocalDate.now(SpaTimeRules.ZONE))) {
            throw new BusinessException("Appointment date cannot be in the past");
        }
        var qualifiedStaff = staffRepository.findQualifiedActiveStaff(serviceId);
        if (staffId != null && qualifiedStaff.stream().noneMatch(staff -> staff.getId().equals(staffId))) {
            throw new BusinessException("Staff is not qualified for this service");
        }
        long duration = SpaTimeRules.durationMinutes(service);
        var result = new java.util.ArrayList<String>();
        var slot = SpaTimeRules.OPEN;
        var close = SpaTimeRules.CLOSE;
        while (java.time.Duration.between(slot, close).toMinutes() >= duration) {
            var currentSlot = slot;
            var end = currentSlot.plusMinutes(duration);
            boolean future = date.atTime(currentSlot).isAfter(java.time.LocalDateTime.now(SpaTimeRules.ZONE));
            boolean available = future && (staffId != null
                    ? isStaffAvailable(staffId, date, currentSlot, end)
                    : qualifiedStaff.stream().anyMatch(staff -> isStaffAvailable(staff.getId(), date, currentSlot, end)));
            available = available && facilities.hasAvailability(serviceId, date, currentSlot, end);
            if (available) result.add(currentSlot.toString());
            slot = slot.plusMinutes(30);
        }
        return result;
    }

    private boolean isStaffAvailable(Long staffId, java.time.LocalDate date, java.time.LocalTime start, java.time.LocalTime end) {
        if (appointmentRepository.existsOverlappingAppointmentForStaff(staffId, date, start, end)) {
            return false;
        }
        if (schedules.hasScheduleOnDate(staffId, date)) {
            return schedules.coversWorkingInterval(staffId, date, start, end);
        }
        // If no explicit shift schedule is registered for this day, staff defaults to working standard store opening hours
        return !start.isBefore(SpaTimeRules.OPEN) && !end.isAfter(SpaTimeRules.CLOSE);
    }
}
