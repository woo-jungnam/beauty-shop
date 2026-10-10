package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.application.dto.response.*;
import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.shared.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;

import java.math.BigDecimal;
import java.util.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Service
@RequiredArgsConstructor
public class AdminSpaCatalogService {
    private final BeautyServiceRepository serviceRepository;
    private final ServiceCategoryRepository categoryRepository;
    private final ServicePackageRepository packageRepository;
    private final AppointmentRepository appointments;
    private final SpaPurchaseSnapshotRepository snapshots;

    @Transactional(readOnly = true)
    public Page<BeautyServiceResponse> services(Pageable pageable) {
        return serviceRepository.findByIsDeletedFalse(pageable).map(BeautyServiceResponse::fromEntity);
    }
    @Transactional(readOnly = true)
    public BeautyServiceResponse service(Long id) {
        return BeautyServiceResponse.fromEntity(getService(id));
    }
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @CacheEvict(value = "spa_services", allEntries = true)
    public BeautyServiceResponse saveService(Long id, ServiceCommand command) {
        validateService(command);
        BeautyService entity = id == null ? new BeautyService() : serviceRepository.findByIdForUpdate(id)
                .filter(value -> !Boolean.TRUE.equals(value.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Spa service not found: " + id));
        serviceRepository.findBySlug(command.slug()).filter(found -> !Objects.equals(found.getId(), id))
                .ifPresent(found -> { throw new BusinessException("Spa service slug already exists"); });
        if (id != null && Boolean.FALSE.equals(command.active())) requireNoServiceObligations(id);
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
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @CacheEvict(value = "spa_services", allEntries = true)
    public void deleteService(Long id) {
        BeautyService entity = serviceRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Spa service not found: " + id));
        requireNoServiceObligations(id);
        entity.setIsDeleted(true); entity.setIsActive(false);
    }

    @Transactional(readOnly = true)
    public Page<CategoryView> categories(Pageable pageable) { return categoryRepository.findByIsDeletedFalse(pageable).map(CategoryView::from); }
    @Transactional(readOnly = true)
    public CategoryView category(Long id) {
        return categoryRepository.findById(id).filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                .map(CategoryView::from)
                .orElseThrow(() -> new ResourceNotFoundException("Spa category not found: " + id));
    }
    @Transactional
    @CacheEvict(value = "spa_services", allEntries = true)
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
    @CacheEvict(value = "spa_services", allEntries = true)
    public void deleteCategory(Long id) {
        ServiceCategory entity = categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Spa category not found"));
        entity.setIsDeleted(true); entity.setIsActive(false);
    }

    @Transactional(readOnly = true)
    public List<ServicePackageResponse> packages() { return packageRepository.findAllAdminWithItems().stream().map(ServicePackageResponse::fromEntity).toList(); }
    @Transactional(readOnly = true)
    public ServicePackageResponse packageDetail(Long id) {
        return packageRepository.findDetailByIdAndIsDeletedFalse(id)
                .map(ServicePackageResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Spa package not found: " + id));
    }
    @Transactional
    public ServicePackageResponse savePackage(Long id, PackageCommand command) {
        if (command == null || command.name() == null || command.name().isBlank() || command.price() == null
                || command.price().compareTo(BigDecimal.ONE) < 0 || command.items() == null || command.items().isEmpty()) {
            throw new BusinessException("Package price and service items are required");
        }
        if (command.validityDays() != null && command.validityDays() <= 0) {
            throw new BusinessException("Package validity days must be positive or null for no expiry");
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
            if (!Boolean.TRUE.equals(service.getIsActive())) throw new BusinessException("Package service is inactive");
            entity.getItems().add(ServicePackageItem.builder().servicePackage(entity).service(service).quantity(item.quantity()).build());
        }
        return ServicePackageResponse.fromEntity(packageRepository.save(entity));
    }
    @Transactional
    public void deletePackage(Long id) { ServicePackage entity = packageRepository.findByIdForUpdateAndIsDeletedFalse(id)
            .orElseThrow(() -> new ResourceNotFoundException("Spa package not found")); entity.setIsDeleted(true); entity.setIsActive(false); }

    private BeautyService getService(Long id) { return serviceRepository.findById(id).filter(value -> !Boolean.TRUE.equals(value.getIsDeleted()))
            .orElseThrow(() -> new ResourceNotFoundException("Spa service not found: " + id)); }
    private void requireNoServiceObligations(Long id) {
        if (appointments.hasOutstandingServiceAppointments(id)
                || snapshots.countServiceOrderObligations(id, java.time.Instant.now()) > 0) {
            throw new BusinessException("Resolve outstanding appointments, paid ticket rights and pending package orders before retiring this service");
        }
    }
    private void validateService(ServiceCommand command) {
        if (command == null || command.name() == null || command.name().isBlank() || command.slug() == null || command.slug().isBlank()) throw new BusinessException("Name and slug are required");
        if (command.basePrice() == null || command.basePrice().signum() < 0 || command.durationMinutes() == null || command.durationMinutes() <= 0) throw new BusinessException("Price and duration are invalid");
        int preparation = command.preparationTimeMinutes() == null ? 15 : command.preparationTimeMinutes();
        if (preparation < 0 || (long) command.durationMinutes() + preparation > 720) {
            throw new BusinessException("Preparation must be nonnegative and the service must fit opening hours");
        }
        if (command.name().trim().length() > 150 || command.slug().trim().length() > 150) {
            throw new BusinessException("Service name and slug must be at most 150 characters");
        }
    }

    @Schema(name = "SpaServiceCommand", description = "Tạo/sửa catalog dịch vụ; không sửa snapshot giá/duration của lịch đã đặt")
    public record ServiceCommand(@Schema(description = "Danh mục chưa xóa; null bỏ liên kết category") Long categoryId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 150, example = "Chăm sóc da cơ bản") String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 150, example = "cham-soc-da-co-ban") String slug,
            String shortDescription, String description,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "VND, có thể 0 cho dịch vụ miễn phí", minimum = "0", example = "300000") BigDecimal basePrice,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Phút dự kiến, duration+preparation phải<=720", minimum = "1", example = "45") Integer durationMinutes,
            @Schema(description = "Phút chuẩn bị; thiếu/null mặc định 15", minimum = "0", example = "15") Integer preparationTimeMinutes,
            String thumbnailUrl, @Schema(description = "Thiếu/null là true cả khi cập nhật; false bị guard nghĩa vụ", example = "true") Boolean active) { }
    @Schema(name = "SpaCategoryCommand", description = "Tạo/sửa danh mục Spa; active thiếu/null là true")
    public record CategoryCommand(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1) String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1) String slug, String description, String thumbnailUrl, Boolean active) { }
    @Schema(name = "SpaCategoryView", description = "Danh mục Spa chưa xóa, có thể inactive")
    public record CategoryView(Long id, String name, String slug, String description, String thumbnailUrl, Boolean active) {
        static CategoryView from(ServiceCategory value) { return new CategoryView(value.getId(), value.getName(), value.getSlug(), value.getDescription(), value.getThumbnailUrl(), value.getIsActive()); }
    }
    @Schema(name = "SpaPackageCommand", description = "Thay toàn bộ catalog gói, không viết lại quyền đã mua")
    public record PackageCommand(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1) String name, String description,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", description = "Giá VND trước làm tròn tạo order", example = "900000") BigDecimal price,
            @Schema(description = "Ngày từ paidAt; null không hết hạn", minimum = "1", nullable = true, example = "30") Integer validityDays, String thumbnailUrl,
            @Schema(description = "Thiếu/null là true cả khi cập nhật") Boolean active,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Không rỗng; serviceId không trùng, dịch vụ hoạt động/chưa xóa") List<PackageItemCommand> items) { }
    @Schema(name = "SpaPackageItemCommand", description = "Quota một dịch vụ trong gói mới/cấu hình mới")
    public record PackageItemCommand(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") Long serviceId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", example = "3") Integer quantity) { }
}
