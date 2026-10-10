package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.shared.exception.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class FacilitySchedulingService {
    private final FacilityRepository facilities;
    private final FacilityBlockRepository blocks;
    private final ServiceFacilityRequirementRepository requirements;
    private final FacilityOccupancyReader occupancy;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Facility findFacility(Long id) {
        return facilities.findById(id).filter(f -> !Boolean.TRUE.equals(f.getIsDeleted()) && Boolean.TRUE.equals(f.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Facility not available: " + id));
    }

    /** Called only for a new booking, while its service rows are locked. */
    @Transactional
    public void snapshotRequirements(List<AppointmentItem> items) {
        for (AppointmentItem item : items) {
            if (item.getResourceRequirements() == null) item.setResourceRequirements(new ArrayList<>());
            if (!item.getResourceRequirements().isEmpty()) continue;
            requirements.findByServiceIdAndIsDeletedFalseOrderByResourceTypeAsc(item.getService().getId()).forEach(row ->
                    item.getResourceRequirements().add(AppointmentItemResourceRequirement.builder()
                            .appointmentItem(item).resourceType(row.getResourceType()).units(row.getUnits()).build()));
            if (item.getResourceRequirements().isEmpty() && item.getFacility() != null) {
                item.getResourceRequirements().add(AppointmentItemResourceRequirement.builder().appointmentItem(item)
                        .resourceType(item.getFacility().getType()).units(1).build());
            }
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void assign(List<AppointmentItem> items, LocalDate date, Long excludedAppointmentId) {
        assignInternal(items, date, excludedAppointmentId, false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void validateOrReassignForReschedule(List<AppointmentItem> items, LocalDate date, Long excludedAppointmentId) {
        assignInternal(items, date, excludedAppointmentId, true);
    }

    /** Caller holds the appointment, staff and ticket mutexes before starting this item. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void requireExecutionCapacity(AppointmentItem item) {
        Map<Long, Integer> unitsByFacility = new TreeMap<>();
        if (item.getFacilityAllocations() != null) item.getFacilityAllocations().stream()
                .filter(allocation -> !Boolean.TRUE.equals(allocation.getIsDeleted()))
                .forEach(allocation -> {
                    if (allocation.getQuantity() == null || allocation.getQuantity() <= 0) {
                        throw new BusinessException("Invalid facility allocation quantity");
                    }
                    unitsByFacility.merge(allocation.getFacility().getId(), allocation.getQuantity(), Math::addExact);
                });
        if (unitsByFacility.isEmpty() && item.getFacility() != null) unitsByFacility.put(item.getFacility().getId(), 1);
        if (unitsByFacility.isEmpty()) {
            if (!demand(item).isEmpty()) throw new BusinessException("Allocate all required facilities before starting this service");
            return;
        }
        List<Facility> locked = new ArrayList<>();
        for (Long id : unitsByFacility.keySet()) locked.add(facilities.findForUpdate(id)
                .orElseThrow(() -> new BusinessException("Allocated facility is no longer available")));
        locked.forEach(entityManager::refresh);
        LocalDateTime now = LocalDateTime.now(SpaTimeRules.ZONE);
        for (Facility facility : locked) {
            if (!Boolean.TRUE.equals(facility.getIsActive()) || Boolean.TRUE.equals(facility.getIsDeleted())
                    || facility.getCapacity() == null || facility.getCapacity() <= 0
                    || blocks.overlaps(facility.getId(), now, now.plusNanos(1))
                    || occupancy.executingUnits(facility.getId(), item.getId()) + unitsByFacility.get(facility.getId()) > facility.getCapacity()) {
                throw new BusinessException("Allocated facility is still occupied or unavailable: " + facility.getId());
            }
        }
    }

    private void assignInternal(List<AppointmentItem> items, LocalDate date, Long excludedAppointmentId, boolean rescheduling) {
        boolean constrained = items.stream().anyMatch(item -> !released(item)
                && (item.getFacility() != null || (item.getResourceRequirements() != null && !item.getResourceRequirements().isEmpty())));
        if (!constrained) return;
        // Sorted row mutexes serialize allocations and administrative maintenance edits.
        List<Facility> locked = facilities.findAllForAllocationUpdate();
        locked.forEach(entityManager::refresh);
        Map<Long, Facility> byId = new LinkedHashMap<>();
        locked.forEach(f -> byId.put(f.getId(), f));
        Map<Long, List<FacilityOccupancyReader.Occupancy>> windows = new HashMap<>();
        locked.forEach(f -> windows.put(f.getId(), new ArrayList<>(occupancy.outstanding(f.getId(), excludedAppointmentId))));
        for (AppointmentItem item : items) {
            if (released(item)) continue;
            requireInterval(date, item.getStartTime(), item.getEndTime());
            Map<String, Integer> demand = demand(item);
            if (demand.isEmpty()) continue;
            boolean forceManual = !rescheduling && item.getFacility() != null
                    && (item.getFacilityAllocations() == null || item.getFacilityAllocations().isEmpty());
            List<Long> preferred = new ArrayList<>();
            if (item.getFacility() != null) preferred.add(item.getFacility().getId());
            if (item.getFacilityAllocations() != null) item.getFacilityAllocations().forEach(a -> preferred.add(a.getFacility().getId()));
            if (forceManual) {
                Facility manual = byId.get(item.getFacility().getId());
                if (manual == null || !demand.containsKey(manual.getType())
                        || available(manual, date, item.getStartTime(), item.getEndTime(), windows.get(manual.getId())) <= 0) {
                    throw new BusinessException("Requested facility is unavailable or does not match this service");
                }
            }
            Map<Long, Integer> chosen = new LinkedHashMap<>();
            for (var requirement : demand.entrySet()) {
                int remaining = requirement.getValue();
                List<Facility> candidates = locked.stream().filter(f -> requirement.getKey().equals(f.getType()))
                        .sorted(Comparator.comparingInt((Facility f) -> preferred.contains(f.getId()) ? preferred.indexOf(f.getId()) : Integer.MAX_VALUE)
                                .thenComparing(Facility::getId)).toList();
                for (Facility facility : candidates) {
                    int free = available(facility, date, item.getStartTime(), item.getEndTime(), windows.get(facility.getId()));
                    int quantity = Math.min(remaining, free);
                    if (quantity <= 0) continue;
                    chosen.put(facility.getId(), quantity);
                    windows.get(facility.getId()).add(new FacilityOccupancyReader.Occupancy(facility.getId(), date,
                            item.getStartTime(), item.getEndTime(), quantity));
                    remaining -= quantity;
                    if (remaining == 0) break;
                }
                if (remaining != 0) throw new BusinessException("Insufficient available facility capacity for type: " + requirement.getKey());
            }
            updateAllocations(item, chosen, byId);
        }
    }

    @Transactional(readOnly = true)
    public boolean hasAvailability(Long serviceId, LocalDate date, LocalTime start, LocalTime end) {
        requireInterval(date, start, end);
        List<Facility> candidates = facilities.findByIsDeletedFalseOrderByIdAsc();
        for (var requirement : requirements.findByServiceIdAndIsDeletedFalseOrderByResourceTypeAsc(serviceId)) {
            long free = candidates.stream().filter(f -> requirement.getResourceType().equals(f.getType()))
                    .mapToLong(f -> available(f, date, start, end, occupancy.outstanding(f.getId(), null))).sum();
            if (free < requirement.getUnits()) return false;
        }
        return true;
    }

    private int available(Facility facility, LocalDate date, LocalTime start, LocalTime end,
                          List<FacilityOccupancyReader.Occupancy> windows) {
        if (!Boolean.TRUE.equals(facility.getIsActive()) || Boolean.TRUE.equals(facility.getIsDeleted())
                || facility.getCapacity() == null || facility.getCapacity() <= 0
                || blocks.overlaps(facility.getId(), date.atTime(start), date.atTime(end))) return 0;
        TreeMap<LocalTime, Long> deltas = new TreeMap<>();
        for (var window : windows) {
            if (!date.equals(window.date()) || !window.start().isBefore(end) || !window.end().isAfter(start)) continue;
            deltas.merge(window.start().isBefore(start) ? start : window.start(), (long) window.quantity(), Long::sum);
            deltas.merge(window.end().isAfter(end) ? end : window.end(), -(long) window.quantity(), Long::sum);
        }
        long current = 0, maximum = 0;
        for (long delta : deltas.values()) { current += delta; maximum = Math.max(maximum, current); }
        return (int) Math.max(0, (long) facility.getCapacity() - maximum);
    }

    private Map<String, Integer> demand(AppointmentItem item) {
        Map<String, Integer> result = new TreeMap<>();
        if (item.getResourceRequirements() != null) item.getResourceRequirements().stream()
                .filter(r -> !Boolean.TRUE.equals(r.getIsDeleted())).forEach(r -> {
                    if (r.getUnits() == null || r.getUnits() <= 0) throw new BusinessException("Invalid resource requirement snapshot");
                    result.merge(r.getResourceType(), r.getUnits(), Math::addExact);
                });
        // Existing legacy single-facility reservations stay constrained without consulting live catalog rules.
        if (result.isEmpty() && item.getFacility() != null) result.put(item.getFacility().getType(), 1);
        return result;
    }

    private void updateAllocations(AppointmentItem item, Map<Long, Integer> chosen, Map<Long, Facility> facilitiesById) {
        if (item.getFacilityAllocations() == null) item.setFacilityAllocations(new ArrayList<>());
        item.getFacilityAllocations().removeIf(a -> !chosen.containsKey(a.getFacility().getId()));
        chosen.forEach((id, quantity) -> {
            FacilityAllocation allocation = item.getFacilityAllocations().stream()
                    .filter(a -> id.equals(a.getFacility().getId())).findFirst().orElse(null);
            if (allocation == null) {
                allocation = FacilityAllocation.builder().appointmentItem(item).facility(facilitiesById.get(id)).build();
                item.getFacilityAllocations().add(allocation);
            }
            allocation.setQuantity(quantity);
        });
        item.setFacility(chosen.isEmpty() ? null : facilitiesById.get(chosen.keySet().iterator().next()));
    }

    private boolean released(AppointmentItem item) {
        return item.getExecutionStatus() != null && (item.getExecutionStatus().name().equals("SKIPPED")
                || item.getExecutionStatus().name().equals("NO_SHOW"));
    }

    private void requireInterval(LocalDate date, LocalTime start, LocalTime end) {
        if (date == null || start == null || end == null || !end.isAfter(start)) throw new BusinessException("Invalid facility reservation interval");
    }
}
