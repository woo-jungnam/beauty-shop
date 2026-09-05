package com.core.beautyshop.modules.spa.application.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.beautyshop.modules.spa.application.dto.response.BeautyServiceResponse;
import com.core.beautyshop.modules.spa.application.service.BeautyServiceService;
import com.core.beautyshop.modules.spa.domain.BeautyServiceRepository;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BeautyServiceServiceImpl implements BeautyServiceService {

    private final BeautyServiceRepository beautyServiceRepository;

    @Override
    public List<BeautyServiceResponse> getAllActiveServices() {
        return beautyServiceRepository.findAllActiveWithCategory().stream()
                .map(BeautyServiceResponse::fromEntity)
                .toList();
    }

    @Override
    public BeautyServiceResponse getServiceById(Long id) {
        return beautyServiceRepository.findWithCategoryById(id)
                .map(BeautyServiceResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dịch vụ spa với ID: " + id));
    }

    @Override
    public BeautyServiceResponse getServiceBySlug(String slug) {
        return beautyServiceRepository.findWithCategoryBySlug(slug)
                .map(BeautyServiceResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dịch vụ spa với slug: " + slug));
    }
}
