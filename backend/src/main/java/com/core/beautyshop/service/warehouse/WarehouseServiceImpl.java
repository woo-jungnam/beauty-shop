package com.core.beautyshop.service.warehouse;

import com.core.beautyshop.dto.request.CreateWarehouseRequest;
import com.core.beautyshop.dto.request.UpdateWarehouseRequest;
import com.core.beautyshop.dto.response.WarehouseResponse;
import com.core.beautyshop.entities.warehouse.Warehouse;
import com.core.beautyshop.entities.warehouse.enums.WarehouseType;
import com.core.beautyshop.exception.BusinessException;
import com.core.beautyshop.exception.ResourceNotFoundException;
import com.core.beautyshop.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WarehouseServiceImpl implements WarehouseService {

    private final WarehouseRepository warehouseRepository;

    @Override
    @Transactional(readOnly = true)
    public List<WarehouseResponse> getAllWarehouses() {
        return warehouseRepository.findByIsDeletedFalse().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public WarehouseResponse getWarehouseById(Long id) {
        Warehouse warehouse = warehouseRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kho với id: " + id));
        return mapToResponse(warehouse);
    }

    @Override
    @Transactional
    public WarehouseResponse createWarehouse(CreateWarehouseRequest request) {
        if (warehouseRepository.existsByCode(request.getCode())) {
            throw new BusinessException("Mã kho đã tồn tại: " + request.getCode());
        }

        Warehouse warehouse = Warehouse.builder()
                .name(request.getName())
                .code(request.getCode())
                .address(request.getAddress())
                .ward(request.getWard())
                .district(request.getDistrict())
                .city(request.getCity())
                .phone(request.getPhone())
                .managerName(request.getManagerName())
                .warehouseType(request.getWarehouseType() != null ? request.getWarehouseType() : WarehouseType.BRANCH)
                .build();

        Warehouse saved = warehouseRepository.save(warehouse);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public WarehouseResponse updateWarehouse(Long id, UpdateWarehouseRequest request) {
        Warehouse warehouse = warehouseRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kho với id: " + id));

        if (request.getName() != null) warehouse.setName(request.getName());
        if (request.getAddress() != null) warehouse.setAddress(request.getAddress());
        if (request.getWard() != null) warehouse.setWard(request.getWard());
        if (request.getDistrict() != null) warehouse.setDistrict(request.getDistrict());
        if (request.getCity() != null) warehouse.setCity(request.getCity());
        if (request.getPhone() != null) warehouse.setPhone(request.getPhone());
        if (request.getManagerName() != null) warehouse.setManagerName(request.getManagerName());
        if (request.getWarehouseType() != null) warehouse.setWarehouseType(request.getWarehouseType());
        if (request.getIsActive() != null) warehouse.setIsActive(request.getIsActive());

        Warehouse saved = warehouseRepository.save(warehouse);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public void deleteWarehouse(Long id) {
        Warehouse warehouse = warehouseRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kho với id: " + id));
        warehouse.setIsDeleted(true);
        warehouseRepository.save(warehouse);
    }

    private WarehouseResponse mapToResponse(Warehouse warehouse) {
        return WarehouseResponse.builder()
                .id(warehouse.getId())
                .name(warehouse.getName())
                .code(warehouse.getCode())
                .address(warehouse.getAddress())
                .ward(warehouse.getWard())
                .district(warehouse.getDistrict())
                .city(warehouse.getCity())
                .phone(warehouse.getPhone())
                .managerName(warehouse.getManagerName())
                .warehouseType(warehouse.getWarehouseType())
                .isActive(warehouse.getIsActive())
                .build();
    }
}
