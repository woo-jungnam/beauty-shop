package com.core.beautyshop.service.spa.impl;

import com.core.beautyshop.dto.response.spa.BeautyServiceResponse;
import com.core.beautyshop.exception.ResourceNotFoundException;
import com.core.beautyshop.repository.BeautyServiceRepository;
import com.core.beautyshop.service.spa.BeautyServiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BeautyServiceServiceImpl implements BeautyServiceService {

    private final BeautyServiceRepository beautyServiceRepository;

    @Override
    public List<BeautyServiceResponse> getAllActiveServices() {
        return beautyServiceRepository.findAll().stream()
                .filter(service -> Boolean.TRUE.equals(service.getIsActive()))
                .map(BeautyServiceResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public BeautyServiceResponse getServiceById(Long id) {
        return beautyServiceRepository.findById(id)
                .map(BeautyServiceResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dịch vụ làm đẹp với id: " + id));
    }

    @Override
    public BeautyServiceResponse getServiceBySlug(String slug) {
        return beautyServiceRepository.findBySlug(slug)
                .map(BeautyServiceResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dịch vụ làm đẹp với slug: " + slug));
    }
}
