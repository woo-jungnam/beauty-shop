package com.core.beautyshop.service.brand;

import com.core.beautyshop.dto.request.CreateBrandRequest;
import com.core.beautyshop.dto.request.UpdateBrandRequest;
import com.core.beautyshop.dto.response.BrandResponse;
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
