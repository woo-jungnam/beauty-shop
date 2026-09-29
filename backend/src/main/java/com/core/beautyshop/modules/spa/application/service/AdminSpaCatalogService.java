package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.application.dto.response.*;
import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.shared.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminSpaCatalogService {
    private final BeautyServiceRepository serviceRepository;
    private final ServiceCategoryRepository categoryRepository;
    private final ServicePackageRepository packageRepository;

    @Transactional(readOnly = true)
    public Page<BeautyServiceResponse> services(Pageable pageable) {
        return serviceRepository.findByIsDeletedFalse(pageable).map(BeautyServiceResponse::fromEntity);
    }
    @Transactional
    public BeautyServiceResponse saveService(Long id, ServiceCommand command) {
        validateService(command);
        BeautyService entity = id == null ? new BeautyService() : serviceRepository.findById(id)
                .filter(value -> !Boolean.TRUE.equals(value.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Spa service not found: " + id));
        serviceRepository.findBySlug(command.slug()).filter(found -> !Objects.equals(found.getId(), id))
                .ifPresent(found -> { throw new BusinessException("Spa service slug already exists"); });
        entity.setName(command.name().trim()); entity.setSlug(command.slug().trim());
        entity.setShortDescription(command.shortDescription()); entity.setDescription(command.description());
        entity.setBasePrice(command.basePrice()); entity.setDurationMinutes(command.durationMinutes());
        entity.setPreparationTimeMinutes(command.preparationTimeMinutes() == null ? 15 : command.preparationTimeMinutes());
        entity.setThumbnailUrl(command.thumbnailUrl()); entity.setIsActive(command.active() == null || command.active());
        entity.setCategory(command.categoryId() == null ? null : categoryRepository.findById(command.categoryId())
                .filter(value -> !Boolean.TRUE.equals(value.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Spa category not found")));
        return BeautyServiceResponse.fromEntity(serviceRepository.save(entity));
    }
    @Transactional
    public void deleteService(Long id) { BeautyService entity = getService(id); entity.setIsDeleted(true); entity.setIsActive(false); }

    @Transactional(readOnly = true)
    public Page<CategoryView> categories(Pageable pageable) { return categoryRepository.findByIsDeletedFalse(pageable).map(CategoryView::from); }
    @Transactional
    public CategoryView saveCategory(Long id, CategoryCommand command) {
        if (command == null || command.name() == null || command.name().isBlank() || command.slug() == null || command.slug().isBlank()) {
            throw new BusinessException("Category name and slug are required");
        }
        ServiceCategory entity = id == null ? new ServiceCategory() : categoryRepository.findById(id)
                .filter(value -> !Boolean.TRUE.equals(value.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Spa category not found: " + id));
        categoryRepository.findBySlug(command.slug()).filter(found -> !Objects.equals(found.getId(), id))
                .ifPresent(found -> { throw new BusinessException("Spa category slug already exists"); });
        entity.setName(command.name().trim()); entity.setSlug(command.slug().trim()); entity.setDescription(command.description());
        entity.setThumbnailUrl(command.thumbnailUrl()); entity.setIsActive(command.active() == null || command.active());
        return CategoryView.from(categoryRepository.save(entity));
    }
    @Transactional
    public void deleteCategory(Long id) {
        ServiceCategory entity = categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Spa category not found"));
        entity.setIsDeleted(true); entity.setIsActive(false);
    }

    @Transactional(readOnly = true)
    public List<ServicePackageResponse> packages() { return packageRepository.findAllAdminWithItems().stream().map(ServicePackageResponse::fromEntity).toList(); }
    @Transactional
    public ServicePackageResponse savePackage(Long id, PackageCommand command) {
        if (command == null || command.name() == null || command.name().isBlank() || command.price() == null
                || command.price().signum() < 0 || command.items() == null || command.items().isEmpty()) {
            throw new BusinessException("Package price and service items are required");
        }
        ServicePackage entity = id == null ? new ServicePackage() : packageRepository.findByIdForUpdateAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Spa package not found: " + id));
        entity.setName(command.name().trim()); entity.setDescription(command.description()); entity.setPrice(command.price());
        entity.setValidityDays(command.validityDays()); entity.setThumbnailUrl(command.thumbnailUrl());
        entity.setIsActive(command.active() == null || command.active());
        if (entity.getItems() == null) entity.setItems(new ArrayList<>()); else entity.getItems().clear();
        Set<Long> seen = new HashSet<>();
        for (PackageItemCommand item : command.items()) {
            if (item.quantity() == null || item.quantity() <= 0 || !seen.add(item.serviceId())) throw new BusinessException("Invalid or duplicate package item");
            BeautyService service = getService(item.serviceId());
            entity.getItems().add(ServicePackageItem.builder().servicePackage(entity).service(service).quantity(item.quantity()).build());
        }
        return ServicePackageResponse.fromEntity(packageRepository.save(entity));
    }
    @Transactional
    public void deletePackage(Long id) { ServicePackage entity = packageRepository.findByIdForUpdateAndIsDeletedFalse(id)
            .orElseThrow(() -> new ResourceNotFoundException("Spa package not found")); entity.setIsDeleted(true); entity.setIsActive(false); }

    private BeautyService getService(Long id) { return serviceRepository.findById(id).filter(value -> !Boolean.TRUE.equals(value.getIsDeleted()))
            .orElseThrow(() -> new ResourceNotFoundException("Spa service not found: " + id)); }
    private void validateService(ServiceCommand command) {
        if (command == null || command.name() == null || command.name().isBlank() || command.slug() == null || command.slug().isBlank()) throw new BusinessException("Name and slug are required");
        if (command.basePrice() == null || command.basePrice().signum() < 0 || command.durationMinutes() == null || command.durationMinutes() <= 0) throw new BusinessException("Price and duration are invalid");
    }

    public record ServiceCommand(Long categoryId, String name, String slug, String shortDescription, String description,
            BigDecimal basePrice, Integer durationMinutes, Integer preparationTimeMinutes, String thumbnailUrl, Boolean active) { }
    public record CategoryCommand(String name, String slug, String description, String thumbnailUrl, Boolean active) { }
    public record CategoryView(Long id, String name, String slug, String description, String thumbnailUrl, Boolean active) {
        static CategoryView from(ServiceCategory value) { return new CategoryView(value.getId(), value.getName(), value.getSlug(), value.getDescription(), value.getThumbnailUrl(), value.getIsActive()); }
    }
    public record PackageCommand(String name, String description, BigDecimal price, Integer validityDays, String thumbnailUrl,
                                 Boolean active, List<PackageItemCommand> items) { }
    public record PackageItemCommand(Long serviceId, Integer quantity) { }
}
