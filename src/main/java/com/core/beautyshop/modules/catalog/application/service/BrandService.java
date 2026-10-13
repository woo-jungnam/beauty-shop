package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.request.CreateBrandRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.UpdateBrandRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.BrandResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BrandService {

    Page<BrandResponse> getAllBrands(Pageable pageable);

    BrandResponse getBrandById(Long id);

    BrandResponse getBrandBySlug(String slug);

    BrandResponse createBrand(CreateBrandRequest request);

    BrandResponse updateBrand(Long id, UpdateBrandRequest request);

    void deleteBrand(Long id);
}
