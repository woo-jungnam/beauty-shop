package com.core.beautyshop.modules.spa.application.service.impl;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
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
import com.core.beautyshop.modules.spa.application.service.FacilitySchedulingService;
import com.core.beautyshop.modules.spa.application.service.SpaBusinessCalendarService;
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
    private final FacilitySchedulingService facilities;
    private final SpaBusinessCalendarService calendar;

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
    public List<String> getAvailableSlots(Long serviceId, LocalDate date, Long staffId) {
        return getAvailableSlots(serviceId, date, staffId, List.of(), null);
    }

    private record SlotService(Long id, long duration, List<Long> staffIds) {}

    @Override
    public List<String> getAvailableSlots(Long serviceId, LocalDate date, Long staffId, List<Long> additional, java.time.Instant validBefore) {
        if (date == null || date.isBefore(LocalDate.now(SpaTimeRules.ZONE))) {
            throw new BusinessException("Ngày hẹn không thể ở trong quá khứ");
        }
        var hours = calendar.forDate(date);
        if (hours.closed()) return List.of();
        List<SlotService> sequence = new ArrayList<>();
        sequence.add(slotService(serviceId, date, staffId));
        if (additional.size() >= Duration.between(hours.opensAt(), hours.closesAt()).toMinutes()) return List.of();
        for (Long id : additional) sequence.add(slotService(id, date, null));
        long total = sequence.stream().mapToLong(SlotService::duration).sum();
        var result = new ArrayList<String>();
        var now = LocalDateTime.now(SpaTimeRules.ZONE);
        for (LocalTime slot = hours.opensAt(); Duration.between(slot, hours.closesAt()).toMinutes() >= total;) {
            LocalTime cursor = slot;
            boolean available = date.atTime(slot).isAfter(now);
            for (var service : sequence) {
                if (!available) break;
                LocalTime start = cursor;
                cursor = start.plusMinutes(service.duration());
                LocalTime end = cursor;
                available = (validBefore == null || date.atTime(start).atZone(SpaTimeRules.ZONE).toInstant().isBefore(validBefore))
                        && service.staffIds().stream().anyMatch(id -> isStaffAvailable(id, date, start, end))
                        && facilities.hasAvailability(service.id(), date, start, end);
            }
            if (available) result.add(slot.toString());
            LocalTime next = slot.plusMinutes(30);
            if (!next.isAfter(slot)) break;
            slot = next;
        }
        return result;
    }

    private SlotService slotService(Long id, LocalDate date, Long requestedStaff) {
        var service = beautyServiceRepository.findWithCategoryById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dịch vụ spa"));
        if (!Boolean.TRUE.equals(service.getIsActive()) || Boolean.TRUE.equals(service.getIsDeleted())) {
            throw new BusinessException("Dịch vụ spa hiện không khả dụng");
        }
        var qualified = staffRepository.findQualifiedActiveStaff(id).stream()
                .filter(person -> requestedStaff == null || requestedStaff.equals(person.getId()))
                .filter(person -> !Boolean.TRUE.equals(service.getRequiresCertification()) || person.getSkills().stream()
                        .anyMatch(skill -> skill.getService().getId().equals(id) && Boolean.TRUE.equals(skill.getIsCertified())
                                && (skill.getCertifiedUntil() == null || !skill.getCertifiedUntil().isBefore(date))))
                .map(person -> person.getId()).toList();
        if (requestedStaff != null && qualified.isEmpty()) throw new BusinessException("Chuyên viên không có kỹ năng phù hợp cho dịch vụ này");
        return new SlotService(id, SpaTimeRules.durationMinutes(service), qualified);
    }

    private boolean isStaffAvailable(Long staffId, LocalDate date, LocalTime start, LocalTime end) {
        if (appointmentRepository.existsOverlappingAppointmentForStaff(staffId, date, start, end)) {
            return false;
        }
        return schedules.coversWorkingInterval(staffId, date, start, end);
    }
}
