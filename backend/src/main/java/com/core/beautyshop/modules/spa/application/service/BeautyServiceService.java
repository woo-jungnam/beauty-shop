package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.application.dto.response.BeautyServiceResponse;
import java.util.List;

public interface BeautyServiceService {
    List<BeautyServiceResponse> getAllActiveServices();
    BeautyServiceResponse getServiceById(Long id);
    BeautyServiceResponse getServiceBySlug(String slug);
}
