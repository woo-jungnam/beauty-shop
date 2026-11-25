package com.core.beautyshop.modules.order.application.service;

import com.core.beautyshop.modules.order.domain.ShipperProfile;
import com.core.beautyshop.modules.order.domain.ShipperProfileRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class ShipperProfileService {

    private final ShipperProfileRepository repository;

    @Transactional(readOnly = true)
    public View getProfile(Long shipperId) {
        return repository.findById(shipperId)
                .map(ShipperProfileService::toView)
                .orElseGet(() -> new View(null, null, null, null, null, null, null));
    }

    @Transactional
    public View updateProfile(Long shipperId, UpdateRequest request) {
        ShipperProfile profile = repository.findById(shipperId).orElseGet(() -> {
            ShipperProfile created = new ShipperProfile();
            created.setUserId(shipperId);
            return created;
        });

        profile.setVehicleType(clean(request.vehicleType()));
        profile.setVehiclePlate(cleanUpper(request.vehiclePlate()));
        profile.setServiceArea(clean(request.serviceArea()));
        profile.setEmergencyContactName(clean(request.emergencyContactName()));
        profile.setEmergencyContactPhone(clean(request.emergencyContactPhone()));
        profile.setWorkShift(clean(request.workShift()));
        profile.setUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));

        return toView(repository.save(profile));
    }

    private static View toView(ShipperProfile profile) {
        return new View(
                profile.getVehicleType(),
                profile.getVehiclePlate(),
                profile.getServiceArea(),
                profile.getEmergencyContactName(),
                profile.getEmergencyContactPhone(),
                profile.getWorkShift(),
                profile.getUpdatedAt()
        );
    }

    private static String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String cleanUpper(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.toUpperCase();
    }

    @Schema(name = "ShipperProfileUpdateRequest", description = "Cập nhật thông tin phương tiện và khu vực hoạt động của tài xế")
    public record UpdateRequest(
            @Size(max = 40, message = "Loại phương tiện tối đa 40 ký tự")
            String vehicleType,

            @Size(max = 30, message = "Biển số xe tối đa 30 ký tự")
            String vehiclePlate,

            @Size(max = 150, message = "Khu vực phụ trách tối đa 150 ký tự")
            String serviceArea,

            @Size(max = 100, message = "Tên liên hệ khẩn cấp tối đa 100 ký tự")
            String emergencyContactName,

            @Size(max = 20, message = "Số điện thoại liên hệ khẩn cấp tối đa 20 ký tự")
            @Pattern(regexp = "^$|^(0|\\+84)[35789]\\d{8}$", message = "Số điện thoại liên hệ khẩn cấp không hợp lệ")
            String emergencyContactPhone,

            @Size(max = 60, message = "Ca làm việc tối đa 60 ký tự")
            String workShift
    ) { }

    @Schema(name = "ShipperProfileView", description = "Thông tin vận hành tài xế")
    public record View(
            String vehicleType,
            String vehiclePlate,
            String serviceArea,
            String emergencyContactName,
            String emergencyContactPhone,
            String workShift,
            Instant updatedAt
    ) { }
}

