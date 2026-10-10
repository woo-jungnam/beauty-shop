package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.request.BannerRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.BannerResponse;

import java.util.List;

public interface BannerService {
    List<BannerResponse> getActiveBanners(String position);
    List<BannerResponse> getAllBannersForAdmin();
    BannerResponse getBannerById(Long id);
    BannerResponse createBanner(BannerRequest request);
    BannerResponse updateBanner(Long id, BannerRequest request);
    void deleteBanner(Long id);
}
