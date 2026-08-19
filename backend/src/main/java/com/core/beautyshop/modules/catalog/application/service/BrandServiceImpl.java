package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.request.CreateBrandRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.UpdateBrandRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.BrandResponse;
import com.core.beautyshop.modules.catalog.domain.Brand;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.modules.catalog.domain.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BrandServiceImpl implements BrandService {

    private final BrandRepository brandRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<BrandResponse> getAllBrands(Pageable pageable) {
        return brandRepository.findByIsDeletedFalse(pageable).map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public BrandResponse getBrandById(Long id) {
        Brand brand = brandRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thương hiệu với id: " + id));
        return mapToResponse(brand);
    }

    @Override
    @Transactional(readOnly = true)
    public BrandResponse getBrandBySlug(String slug) {
        Brand brand = brandRepository.findBySlugAndIsDeletedFalse(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with slug: " + slug));
        return mapToResponse(brand);
    }

    @Override
    @Transactional
    public BrandResponse createBrand(CreateBrandRequest request) {
        if (brandRepository.existsBySlug(request.getSlug())) {
            throw new BusinessException("Slug thương hiệu đã tồn tại: " + request.getSlug());
        }

        Brand brand = Brand.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .logoUrl(request.getLogoUrl())
                .description(request.getDescription())
                .originCountry(request.getOriginCountry())
                .isOfficial(request.getIsOfficial() != null ? request.getIsOfficial() : false)
                .build();

        Brand saved = brandRepository.save(brand);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public BrandResponse updateBrand(Long id, UpdateBrandRequest request) {
        Brand brand = brandRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thương hiệu với id: " + id));

        if (request.getName() != null) brand.setName(request.getName());
        if (request.getSlug() != null) {
            if (!brand.getSlug().equals(request.getSlug()) && brandRepository.existsBySlug(request.getSlug())) {
                throw new BusinessException("Slug thương hiệu đã tồn tại: " + request.getSlug());
            }
            brand.setSlug(request.getSlug());
        }
        if (request.getLogoUrl() != null) brand.setLogoUrl(request.getLogoUrl());
        if (request.getDescription() != null) brand.setDescription(request.getDescription());
        if (request.getOriginCountry() != null) brand.setOriginCountry(request.getOriginCountry());
        if (request.getIsOfficial() != null) brand.setIsOfficial(request.getIsOfficial());

        Brand saved = brandRepository.save(brand);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public void deleteBrand(Long id) {
        Brand brand = brandRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thương hiệu với id: " + id));
        brand.setIsDeleted(true);
        brandRepository.save(brand);
    }

    private BrandResponse mapToResponse(Brand brand) {
        return BrandResponse.builder()
                .id(brand.getId())
                .name(brand.getName())
                .slug(brand.getSlug())
                .logoUrl(brand.getLogoUrl())
                .description(brand.getDescription())
                .originCountry(brand.getOriginCountry())
                .isOfficial(brand.getIsOfficial())
                .build();
    }
}
