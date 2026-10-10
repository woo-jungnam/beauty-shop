package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.shared.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Service @RequiredArgsConstructor
public class ServiceResourceRequirementService {
    private final BeautyServiceRepository services;
    private final ServiceFacilityRequirementRepository requirements;

    @Transactional(readOnly = true)
    public List<RequirementCommand> get(Long serviceId) {
        services.findById(serviceId).filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Spa service not found"));
        return requirements.findByServiceIdAndIsDeletedFalseOrderByResourceTypeAsc(serviceId).stream()
                .map(r -> new RequirementCommand(r.getResourceType(), r.getUnits())).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<RequirementCommand> replace(Long serviceId, List<RequirementCommand> commands) {
        BeautyService service = services.findByIdForUpdate(serviceId).filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Spa service not found"));
        if (commands == null) throw new BusinessException("Resource requirements are required; use an empty array to remove constraints");
        Map<String, Integer> validated = new TreeMap<>();
        for (RequirementCommand command : commands) {
            if (command == null || command.units() == null || command.units() <= 0) throw new BusinessException("Resource units must be positive");
            String type = AdminFacilityService.normalizeType(command.type());
            if (validated.putIfAbsent(type, command.units()) != null) throw new BusinessException("Duplicate resource type: " + type);
        }
        requirements.deleteAll(requirements.findByServiceIdAndIsDeletedFalseOrderByResourceTypeAsc(serviceId));
        requirements.flush();
        requirements.saveAll(validated.entrySet().stream().map(entry -> ServiceFacilityRequirement.builder()
                .service(service).resourceType(entry.getKey()).units(entry.getValue()).build()).toList());
        return validated.entrySet().stream().map(entry -> new RequirementCommand(entry.getKey(), entry.getValue())).toList();
    }
    @Schema(name = "SpaServiceResourceRequirement", description = "Một loại resource và số đơn vị đồng thời mà dịch vụ cần; list thay toàn bộ cho booking mới")
    public record RequirementCommand(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Trim/chữ hoa; mã kết quả khớp [A-Z][A-Z0-9_]{0,39} và không trùng trong list", example = "BED") String type,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", example = "1") Integer units) { }
}
