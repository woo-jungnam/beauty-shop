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
        return staffRepository.findQualifiedActiveStaff(serviceId).stream()
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
        if (date.isBefore(java.time.LocalDate.now())) {
            throw new BusinessException("Appointment date cannot be in the past");
        }
        var qualifiedStaff = staffRepository.findQualifiedActiveStaff(serviceId);
        if (staffId != null && qualifiedStaff.stream().noneMatch(staff -> staff.getId().equals(staffId))) {
            throw new BusinessException("Staff is not qualified for this service");
        }
        long duration = (long) service.getDurationMinutes()
                + (service.getPreparationTimeMinutes() == null ? 0 : service.getPreparationTimeMinutes());
        var result = new java.util.ArrayList<String>();
        var slot = java.time.LocalTime.of(8, 0);
        var close = java.time.LocalTime.of(20, 0);
        while (!slot.plusMinutes(duration).isAfter(close)) {
            var currentSlot = slot;
            var end = currentSlot.plusMinutes(duration);
            boolean future = !date.equals(java.time.LocalDate.now()) || currentSlot.isAfter(java.time.LocalTime.now());
            boolean available = future && (staffId != null
                    ? !appointmentRepository.existsOverlappingAppointmentForStaff(staffId, date, currentSlot, end)
                    : qualifiedStaff.isEmpty() || qualifiedStaff.stream().anyMatch(staff ->
                            !appointmentRepository.existsOverlappingAppointmentForStaff(staff.getId(), date, currentSlot, end)));
            if (available) result.add(currentSlot.toString());
            slot = slot.plusMinutes(30);
        }
        return result;
    }
}
