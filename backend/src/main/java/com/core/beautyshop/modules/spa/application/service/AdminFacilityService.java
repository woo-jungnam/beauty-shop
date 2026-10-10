package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.shared.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Service @RequiredArgsConstructor
public class AdminFacilityService {
    private final FacilityRepository facilities;
    private final FacilityBlockRepository blocks;
    private final FacilityOccupancyReader occupancy;

    @Transactional(readOnly = true)
    public List<FacilityView> list() { return facilities.findByIsDeletedFalseOrderByIdAsc().stream().map(FacilityView::from).toList(); }
    @Transactional(readOnly = true)
    public FacilityView get(Long id) { return FacilityView.from(find(id)); }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FacilityView save(Long id, FacilityCommand command) {
        if (command == null || command.name() == null || command.name().isBlank() || command.name().trim().length() > 100
                || (command.description() != null && command.description().length() > 250)) {
            throw new BusinessException("Facility name and description are invalid");
        }
        Facility facility = id == null ? new Facility() : lock(id);
        String type = command.type() == null ? (id == null ? "GENERAL" : facility.getType()) : normalizeType(command.type());
        int capacity = command.capacity() == null ? (id == null ? 1 : facility.getCapacity()) : command.capacity();
        if (capacity <= 0) throw new BusinessException("Facility capacity must be positive");
        boolean active = command.active() == null ? (id == null || Boolean.TRUE.equals(facility.getIsActive())) : command.active();
        if (id != null && (!active || !Objects.equals(type, facility.getType()) || capacity < facility.getCapacity())) {
            requireNoOutstanding(id);
        }
        facility.setName(command.name().trim()); facility.setDescription(command.description());
        facility.setType(type); facility.setCapacity(capacity); facility.setIsActive(active);
        return FacilityView.from(facilities.save(facility));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(Long id) {
        Facility facility = lock(id); requireNoOutstanding(id);
        facility.setIsActive(false); facility.setIsDeleted(true);
    }

    @Transactional(readOnly = true)
    public List<BlockView> blocks(Long id) {
        find(id); return blocks.findByFacilityIdAndIsDeletedFalseOrderByStartAtAsc(id).stream().map(BlockView::from).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BlockView saveBlock(Long facilityId, Long blockId, BlockCommand command) {
        if (command == null || command.startAt() == null || command.endAt() == null || !command.endAt().isAfter(command.startAt())
                || command.reason() == null || command.reason().isBlank() || command.reason().trim().length() > 500) {
            throw new BusinessException("Facility block needs an ordered interval and a reason");
        }
        Facility facility = lock(facilityId);
        LocalDateTime now = LocalDateTime.now(SpaTimeRules.ZONE);
        boolean blocksCurrentExecution = !command.startAt().isAfter(now) && command.endAt().isAfter(now)
                && occupancy.executingUnits(facilityId, null) > 0;
        if (blocksCurrentExecution || occupancy.hasOutstandingDuring(facilityId, command.startAt(), command.endAt())) {
            throw new BusinessException("Reassign or cancel appointments before blocking this facility");
        }
        FacilityBlock block = blockId == null ? new FacilityBlock() : blocks.findById(blockId)
                .filter(b -> !Boolean.TRUE.equals(b.getIsDeleted()) && facilityId.equals(b.getFacility().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Facility block not found"));
        block.setFacility(facility); block.setStartAt(command.startAt()); block.setEndAt(command.endAt()); block.setReason(command.reason().trim());
        return BlockView.from(blocks.save(block));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteBlock(Long facilityId, Long blockId) {
        lock(facilityId);
        FacilityBlock block = blocks.findById(blockId)
                .filter(b -> !Boolean.TRUE.equals(b.getIsDeleted()) && facilityId.equals(b.getFacility().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Facility block not found"));
        block.setIsDeleted(true);
    }

    private Facility find(Long id) { return facilities.findById(id).filter(f -> !Boolean.TRUE.equals(f.getIsDeleted()))
            .orElseThrow(() -> new ResourceNotFoundException("Facility not found: " + id)); }
    private Facility lock(Long id) { return facilities.findForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("Facility not found: " + id)); }
    private void requireNoOutstanding(Long id) {
        if (occupancy.hasOutstanding(id)) throw new BusinessException("Reassign or cancel outstanding facility reservations first");
    }
    public static String normalizeType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z][A-Z0-9_]{0,39}")) throw new BusinessException("Resource type must be a code of at most 40 characters");
        return normalized;
    }
    @Schema(name = "SpaFacilityCommand", description = "Tạo/sửa resource; khi sửa bỏ type/capacity/active giữ hiện tại, name vẫn bắt buộc")
    public record FacilityCommand(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 100, example = "Giường 1") String name,
            @Schema(maxLength = 250) String description,
            @Schema(description = "Trim và chuẩn hóa chữ hoa; mã kết quả phải khớp [A-Z][A-Z0-9_]{0,39}. Tạo mới thiếu/null dùng GENERAL", example = "BED") String type,
            @Schema(description = "Số đơn vị đồng thời; tạo mới thiếu/null dùng 1", minimum = "1", example = "1") Integer capacity,
            @Schema(description = "Tạo mới thiếu/null dùng true; sửa thiếu/null giữ hiện tại", example = "true") Boolean active) { }
    @Schema(name = "SpaFacilityView", description = "Resource quản trị, gồm type/capacity và active")
    public record FacilityView(Long id, String name, String description, String type, Integer capacity, Boolean active) {
        public static FacilityView from(Facility facility) { return new FacilityView(facility.getId(), facility.getName(), facility.getDescription(), facility.getType(), facility.getCapacity(), facility.getIsActive()); }
    }
    @Schema(name = "SpaFacilityBlockCommand", description = "Khoảng bảo trì giữ toàn bộ capacity, ngày giờ địa phương Việt Nam không có offset")
    public record BlockCommand(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", example = "2026-10-05T12:00:00") LocalDateTime startAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Phải sau startAt", type = "string", example = "2026-10-05T14:00:00") LocalDateTime endAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 500, example = "Bảo trì thiết bị") String reason) { }
    @Schema(name = "SpaFacilityBlockView", description = "Khoảng ngừng phục vụ địa phương của resource; startAt/endAt không có offset")
    public record BlockView(Long id, Long facilityId, LocalDateTime startAt, LocalDateTime endAt, String reason) {
        public static BlockView from(FacilityBlock block) { return new BlockView(block.getId(), block.getFacility().getId(), block.getStartAt(), block.getEndAt(), block.getReason()); }
    }
}
