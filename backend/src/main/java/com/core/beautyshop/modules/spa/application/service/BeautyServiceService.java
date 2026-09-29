package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.application.dto.response.BeautyServiceResponse;
import java.util.List;
import java.time.LocalDate;
import com.core.beautyshop.modules.spa.application.dto.response.ServicePackageResponse;
import com.core.beautyshop.modules.spa.application.dto.response.StaffResponse;

public interface BeautyServiceService {
    List<BeautyServiceResponse> getAllActiveServices();
    BeautyServiceResponse getServiceById(Long id);
    BeautyServiceResponse getServiceBySlug(String slug);
    List<ServicePackageResponse> getActivePackages();
    List<StaffResponse> getQualifiedStaff(Long serviceId);
    List<String> getAvailableSlots(Long serviceId, LocalDate date, Long staffId);
}
