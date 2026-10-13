package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.request.BannerRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.BannerResponse;
import com.core.beautyshop.modules.catalog.domain.Banner;
import com.core.beautyshop.modules.catalog.domain.BannerRepository;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {

    private final BannerRepository bannerRepository;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "banners", key = "'active:' + (#position != null ? #position : 'all')")
    public List<BannerResponse> getActiveBanners(String position) {
        List<Banner> list = (position != null && !position.isBlank())
                ? bannerRepository.findByPositionAndIsDeletedFalseAndIsActiveTrueOrderBySortOrderAscIdAsc(position)
                : bannerRepository.findByIsDeletedFalseAndIsActiveTrueOrderBySortOrderAscIdAsc();
        return list.stream().map(BannerResponse::fromEntity).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BannerResponse> getAllBannersForAdmin() {
        return bannerRepository.findByIsDeletedFalseOrderByPositionAscSortOrderAscIdAsc()
                .stream().map(BannerResponse::fromEntity).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BannerResponse getBannerById(Long id) {
        Banner banner = bannerRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy banner với ID: " + id));
        return BannerResponse.fromEntity(banner);
    }

    @Override
    @Transactional
    @CacheEvict(value = "banners", allEntries = true)
    public BannerResponse createBanner(BannerRequest request) {
        Banner banner = Banner.builder()
                .title(request.getTitle())
                .badge(request.getBadge())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .targetUrl(request.getTargetUrl())
                .ctaText(request.getCtaText())
                .position(request.getPosition() != null && !request.getPosition().isBlank() ? request.getPosition() : "HERO_SLIDE")
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();
        Banner saved = bannerRepository.save(banner);
        return BannerResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = "banners", allEntries = true)
    public BannerResponse updateBanner(Long id, BannerRequest request) {
        Banner banner = bannerRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy banner với ID: " + id));

        banner.setTitle(request.getTitle());
        banner.setBadge(request.getBadge());
        banner.setDescription(request.getDescription());
        banner.setImageUrl(request.getImageUrl());
        banner.setTargetUrl(request.getTargetUrl());
        banner.setCtaText(request.getCtaText());
        if (request.getPosition() != null && !request.getPosition().isBlank()) {
            banner.setPosition(request.getPosition());
        }
        if (request.getSortOrder() != null) {
            banner.setSortOrder(request.getSortOrder());
        }
        if (request.getIsActive() != null) {
            banner.setIsActive(request.getIsActive());
        }

        Banner updated = bannerRepository.save(banner);
        return BannerResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    @CacheEvict(value = "banners", allEntries = true)
    public void deleteBanner(Long id) {
        Banner banner = bannerRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy banner với ID: " + id));
        banner.setIsDeleted(true);
        bannerRepository.save(banner);
    }
}
