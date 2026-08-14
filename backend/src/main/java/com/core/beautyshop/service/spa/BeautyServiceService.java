package com.core.beautyshop.service.spa;

import com.core.beautyshop.dto.response.spa.BeautyServiceResponse;
import java.util.List;

public interface BeautyServiceService {
    List<BeautyServiceResponse> getAllActiveServices();
    BeautyServiceResponse getServiceById(Long id);
    BeautyServiceResponse getServiceBySlug(String slug);
}
