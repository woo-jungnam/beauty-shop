package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.enums.*;
import com.core.beautyshop.modules.identity.domain.User;
import com.core.beautyshop.modules.identity.domain.UserRepository;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test")
class FacilitySchedulingIntegrationTest {
    @Autowired FacilitySchedulingService scheduling;
    @Autowired FacilityOccupancyReader occupancy;
    @Autowired AdminFacilityService administration;
    @Autowired ServiceResourceRequirementService configuration;
    @Autowired FacilityRepository facilities;
    @Autowired FacilityBlockRepository blocks;
    @Autowired BeautyServiceRepository services;
    @Autowired ServiceFacilityRequirementRepository requirements;
    @Autowired AppointmentRepository appointments;
    @Autowired StaffRepository staff;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactions;
    final List<Long> appointmentIds = Collections.synchronizedList(new ArrayList<>());
    final List<Long> facilityIds = new ArrayList<>(), serviceIds = new ArrayList<>(), staffIds = new ArrayList<>(), userIds = new ArrayList<>();

    <T> T tx(Supplier<T> action) {
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return template.execute(status -> action.get());
    }
    record Fixture(long serviceId, long bedId, long staffA, long staffB, String bedType, String machineType, LocalDate date) { }
    Fixture fixture(int capacity) {
        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
        var service = tx(() -> services.saveAndFlush(BeautyService.builder().name("Facial").slug(UUID.randomUUID().toString())
                .durationMinutes(45).preparationTimeMinutes(15).basePrice(BigDecimal.valueOf(300000)).build()));
        serviceIds.add(service.getId());
        var bed = administration.save(null, new AdminFacilityService.FacilityCommand("Bed", null, "BED_" + code, capacity, true));
        facilityIds.add(bed.id());
        long userA = tx(() -> users.saveAndFlush(User.builder().username(UUID.randomUUID().toString()).fullName("Technician A").build()).getId());
        long userB = tx(() -> users.saveAndFlush(User.builder().username(UUID.randomUUID().toString()).fullName("Technician B").build()).getId());
        userIds.add(userA); userIds.add(userB);
        long staffA = tx(() -> staff.saveAndFlush(Staff.builder().userId(userA).build()).getId());
        long staffB = tx(() -> staff.saveAndFlush(Staff.builder().userId(userB).build()).getId());
        staffIds.add(staffA); staffIds.add(staffB);
        configuration.replace(service.getId(), List.of(new ServiceResourceRequirementService.RequirementCommand(bed.type(), 1)));
        return new Fixture(service.getId(), bed.id(), staffA, staffB, bed.type(), "MACHINE_" + code, LocalDate.now(SpaTimeRules.ZONE).plusDays(8));
    }
    long book(Fixture fixture, long staffId, LocalTime start, LocalTime end) {
        return tx(() -> {
            Appointment appointment = Appointment.builder().userId(500L).appointmentDate(fixture.date()).startTime(start).endTime(end)
                    .status(AppointmentStatus.CONFIRMED).items(new ArrayList<>()).build();
            appointment.getItems().add(AppointmentItem.builder().appointment(appointment).service(services.findById(fixture.serviceId()).orElseThrow())
                    .staff(staff.findById(staffId).orElseThrow()).startTime(start).endTime(end).price(BigDecimal.TEN).build());
            scheduling.snapshotRequirements(appointment.getItems());
            scheduling.assign(appointment.getItems(), fixture.date(), null);
            long id = appointments.saveAndFlush(appointment).getId(); appointmentIds.add(id); return id;
        });
    }
    @AfterEach void cleanup() {
        tx(() -> {
            appointments.deleteAllById(appointmentIds); appointments.flush();
            for (Long id : facilityIds) blocks.deleteAll(blocks.findByFacilityId(id));
            blocks.flush(); facilities.deleteAllById(facilityIds); facilities.flush();
            for (Long id : serviceIds) requirements.deleteAll(requirements.findByServiceIdAndIsDeletedFalseOrderByResourceTypeAsc(id));
            requirements.flush(); services.deleteAllById(serviceIds); staff.deleteAllById(staffIds); staff.flush();
            users.deleteAllById(userIds); return null;
        });
    }

    @Test void twoDifferentStaffCannotReserveOneBedAndCancellationReleasesIt() {
        Fixture fixture = fixture(1);
        long first = book(fixture, fixture.staffA(), LocalTime.of(10, 0), LocalTime.of(11, 0));
        assertNotEquals(fixture.staffA(), fixture.staffB());
        var reserved = occupancy.outstanding(fixture.bedId(), null);
        assertEquals(1, reserved.size());
        assertEquals(LocalTime.of(10, 0), reserved.getFirst().start(), "JDBC must read the same business time as JPA");
        assertEquals(LocalTime.of(11, 0), reserved.getFirst().end());
        assertThrows(BusinessException.class, () -> book(fixture, fixture.staffB(), LocalTime.of(10, 0), LocalTime.of(11, 0)));
        tx(() -> { appointments.findById(first).orElseThrow().setStatus(AppointmentStatus.CANCELLED); return null; });
        assertDoesNotThrow(() -> book(fixture, fixture.staffB(), LocalTime.of(10, 0), LocalTime.of(11, 0)));
    }

    @Test void bedAndMachineAreBothRequired() {
        Fixture fixture = fixture(1);
        configuration.replace(fixture.serviceId(), List.of(new ServiceResourceRequirementService.RequirementCommand(fixture.bedType(), 1),
                new ServiceResourceRequirementService.RequirementCommand(fixture.machineType(), 1)));
        assertFalse(scheduling.hasAvailability(fixture.serviceId(), fixture.date(), LocalTime.of(10, 0), LocalTime.of(11, 0)));
        assertThrows(BusinessException.class, () -> book(fixture, fixture.staffA(), LocalTime.of(10, 0), LocalTime.of(11, 0)));
        var machine = administration.save(null, new AdminFacilityService.FacilityCommand("Machine", null, fixture.machineType(), 1, true));
        facilityIds.add(machine.id());
        long id = book(fixture, fixture.staffA(), LocalTime.of(10, 0), LocalTime.of(11, 0));
        tx(() -> { var item = appointments.findById(id).orElseThrow().getItems().getFirst();
            assertEquals(2, item.getFacilityAllocations().size()); assertEquals(2, item.getResourceRequirements().size()); return null; });
    }

    @Test void skippedItemReleasesCapacityWhileAppointmentRemainsInProgress() {
        Fixture fixture = fixture(1);
        long id = book(fixture, fixture.staffA(), LocalTime.of(10, 0), LocalTime.of(11, 0));
        tx(() -> { var appointment = appointments.findById(id).orElseThrow(); appointment.setStatus(AppointmentStatus.IN_PROGRESS);
            appointment.getItems().getFirst().setExecutionStatus(AppointmentItemExecutionStatus.SKIPPED); return null; });
        assertTrue(scheduling.hasAvailability(fixture.serviceId(), fixture.date(), LocalTime.of(10, 0), LocalTime.of(11, 0)));
        assertDoesNotThrow(() -> book(fixture, fixture.staffB(), LocalTime.of(10, 0), LocalTime.of(11, 0)));
    }

    @Test void conflictingRescheduleRollsBackTimesAndAllocations() {
        Fixture fixture = fixture(1);
        long first = book(fixture, fixture.staffA(), LocalTime.of(10, 0), LocalTime.of(11, 0));
        book(fixture, fixture.staffB(), LocalTime.of(12, 0), LocalTime.of(13, 0));
        assertThrows(BusinessException.class, () -> tx(() -> {
            var appointment = appointments.findByIdForUpdate(first).orElseThrow();
            appointment.setStartTime(LocalTime.of(12, 0)); appointment.setEndTime(LocalTime.of(13, 0));
            var item = appointment.getItems().getFirst(); item.setStartTime(appointment.getStartTime()); item.setEndTime(appointment.getEndTime());
            scheduling.validateOrReassignForReschedule(appointment.getItems(), fixture.date(), first); return null;
        }));
        tx(() -> { var appointment = appointments.findById(first).orElseThrow();
            assertEquals(LocalTime.of(10, 0), appointment.getStartTime());
            assertEquals(LocalTime.of(10, 0), appointment.getItems().getFirst().getStartTime());
            assertEquals(fixture.bedId(), appointment.getItems().getFirst().getFacilityAllocations().getFirst().getFacility().getId()); return null; });
    }

    @Test void editedCatalogRequirementsDoNotChangeExistingBookingSnapshot() {
        Fixture fixture = fixture(1);
        long first = book(fixture, fixture.staffA(), LocalTime.of(10, 0), LocalTime.of(11, 0));
        configuration.replace(fixture.serviceId(), List.of(new ServiceResourceRequirementService.RequirementCommand(fixture.bedType(), 1),
                new ServiceResourceRequirementService.RequirementCommand(fixture.machineType(), 1)));
        tx(() -> { var appointment = appointments.findByIdForUpdate(first).orElseThrow(); var item = appointment.getItems().getFirst();
            appointment.setStartTime(LocalTime.of(14, 0)); appointment.setEndTime(LocalTime.of(15, 0));
            item.setStartTime(appointment.getStartTime()); item.setEndTime(appointment.getEndTime());
            scheduling.validateOrReassignForReschedule(appointment.getItems(), fixture.date(), first);
            assertEquals(1, item.getResourceRequirements().size()); return null; });
        assertThrows(BusinessException.class, () -> book(fixture, fixture.staffB(), LocalTime.of(16, 0), LocalTime.of(17, 0)));
    }

    @Test void maintenanceAndRetirementCannotInvalidateOutstandingReservation() {
        Fixture fixture = fixture(1);
        book(fixture, fixture.staffA(), LocalTime.of(10, 0), LocalTime.of(11, 0));
        assertThrows(BusinessException.class, () -> administration.delete(fixture.bedId()));
        assertThrows(BusinessException.class, () -> administration.save(fixture.bedId(), new AdminFacilityService.FacilityCommand("Bed", null, fixture.bedType(), 1, false)));
        assertThrows(BusinessException.class, () -> administration.saveBlock(fixture.bedId(), null,
                new AdminFacilityService.BlockCommand(fixture.date().atTime(10, 30), fixture.date().atTime(12, 0), "Maintenance")));
        var block = administration.saveBlock(fixture.bedId(), null,
                new AdminFacilityService.BlockCommand(fixture.date().atTime(12, 0), fixture.date().atTime(14, 0), "Maintenance"));
        assertFalse(scheduling.hasAvailability(fixture.serviceId(), fixture.date(), LocalTime.of(12, 0), LocalTime.of(13, 0)));
        administration.deleteBlock(fixture.bedId(), block.id());
        assertTrue(scheduling.hasAvailability(fixture.serviceId(), fixture.date(), LocalTime.of(12, 0), LocalTime.of(13, 0)));
    }

    @Test void capacityUsesPeakConcurrencyRatherThanSumOfSequentialReservations() {
        Fixture fixture = fixture(2);
        book(fixture, fixture.staffA(), LocalTime.of(10, 0), LocalTime.of(10, 30));
        book(fixture, fixture.staffB(), LocalTime.of(10, 30), LocalTime.of(11, 0));
        assertDoesNotThrow(() -> book(fixture, fixture.staffA(), LocalTime.of(10, 0), LocalTime.of(11, 0)));
        assertThrows(BusinessException.class, () -> book(fixture, fixture.staffB(), LocalTime.of(10, 0), LocalTime.of(11, 0)));
        assertThrows(BusinessException.class, () -> administration.save(fixture.bedId(), new AdminFacilityService.FacilityCommand("Bed", null, fixture.bedType(), 1, true)));
    }

    @Test void legacySingleFacilityIsCountedOnceAfterAddingDetailedAllocation() {
        Fixture fixture = fixture(2);
        long first = tx(() -> {
            var appointment = Appointment.builder().userId(500L).appointmentDate(fixture.date())
                    .startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(11, 0))
                    .status(AppointmentStatus.CONFIRMED).items(new ArrayList<>()).build();
            appointment.getItems().add(AppointmentItem.builder().appointment(appointment)
                    .service(services.findById(fixture.serviceId()).orElseThrow()).facility(facilities.findById(fixture.bedId()).orElseThrow())
                    .startTime(appointment.getStartTime()).endTime(appointment.getEndTime()).price(BigDecimal.TEN).build());
            long id = appointments.saveAndFlush(appointment).getId(); appointmentIds.add(id); return id;
        });
        assertTrue(scheduling.hasAvailability(fixture.serviceId(), fixture.date(), LocalTime.of(10, 0), LocalTime.of(11, 0)));
        tx(() -> { var item = appointments.findById(first).orElseThrow().getItems().getFirst();
            item.getFacilityAllocations().add(FacilityAllocation.builder().appointmentItem(item).facility(item.getFacility()).quantity(1).build());
            appointments.flush(); return null; });
        assertDoesNotThrow(() -> book(fixture, fixture.staffB(), LocalTime.of(10, 0), LocalTime.of(11, 0)));
        assertThrows(BusinessException.class, () -> book(fixture, fixture.staffA(), LocalTime.of(10, 0), LocalTime.of(11, 0)));
    }

    @Test void simultaneousAllocationsForOneBedAcceptExactlyOneBooking() throws Exception {
        Fixture fixture = fixture(1);
        CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (long staffId : List.of(fixture.staffA(), fixture.staffB())) attempts.add(pool.submit(() -> {
                ready.countDown(); if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start barrier timed out");
                try { book(fixture, staffId, LocalTime.of(10, 0), LocalTime.of(11, 0)); return true; }
                catch (BusinessException expected) { return false; }
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS)); go.countDown();
            int accepted = 0; for (Future<Boolean> attempt : attempts) if (attempt.get(20, TimeUnit.SECONDS)) accepted++;
            assertEquals(1, accepted);
        }
    }

    @Test void actualExecutionOverrunBlocksNextPlannedSlotUntilServiceFinishes() {
        Fixture fixture = fixture(1);
        long first = book(fixture, fixture.staffA(), LocalTime.of(10, 0), LocalTime.of(11, 0));
        long next = book(fixture, fixture.staffB(), LocalTime.of(11, 0), LocalTime.of(12, 0));
        tx(() -> {
            var appointment = appointments.findById(first).orElseThrow();
            appointment.setStatus(AppointmentStatus.IN_PROGRESS);
            var item = appointment.getItems().getFirst();
            item.setExecutionStatus(AppointmentItemExecutionStatus.IN_PROGRESS);
            item.setActualStartedAt(Instant.now().minusSeconds(7200));
            appointments.findById(next).orElseThrow().setStatus(AppointmentStatus.IN_PROGRESS);
            return null;
        });
        assertEquals(1, occupancy.executingUnits(fixture.bedId(), null), "The legacy facility mirror must not double count allocations");
        LocalDateTime now = LocalDateTime.now(SpaTimeRules.ZONE);
        assertThrows(BusinessException.class, () -> administration.saveBlock(fixture.bedId(), null,
                new AdminFacilityService.BlockCommand(now.minusMinutes(1), now.plusMinutes(1), "Immediate maintenance")));
        assertThrows(BusinessException.class, () -> tx(() -> {
            scheduling.requireExecutionCapacity(appointments.findById(next).orElseThrow().getItems().getFirst());
            return null;
        }));
        tx(() -> {
            var item = appointments.findById(first).orElseThrow().getItems().getFirst();
            item.setExecutionStatus(AppointmentItemExecutionStatus.PERFORMED);
            item.setActualCompletedAt(Instant.now());
            return null;
        });
        assertEquals(0, occupancy.executingUnits(fixture.bedId(), null));
        assertDoesNotThrow(() -> tx(() -> {
            var item = appointments.findById(next).orElseThrow().getItems().getFirst();
            scheduling.requireExecutionCapacity(item);
            item.setExecutionStatus(AppointmentItemExecutionStatus.IN_PROGRESS);
            return null;
        }));
        assertEquals(1, occupancy.executingUnits(fixture.bedId(), null));
    }
}
