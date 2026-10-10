package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.enums.*;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class StaffShiftIntegrityIntegrationTest {
    @Autowired StaffRepository staff;
    @Autowired StaffServiceSkillRepository skills;
    @Autowired StaffScheduleRepository shifts;
    @Autowired BeautyServiceRepository services;
    @Autowired AppointmentRepository appointments;
    @Autowired AdminStaffService administration;
    @Autowired PlatformTransactionManager transactions;

    <T> T tx(Supplier<T> action) { return new TransactionTemplate(transactions).execute(status -> action.get()); }
    record Fixture(long staffId, long serviceId, long shiftId, LocalDate date) { }
    Fixture fixture() {
        return tx(() -> {
            var service = services.save(BeautyService.builder().name("Facial").slug(UUID.randomUUID().toString())
                    .basePrice(BigDecimal.TEN).durationMinutes(45).preparationTimeMinutes(15).build());
            var technician = staff.save(Staff.builder().userId(Math.abs(UUID.randomUUID().getMostSignificantBits())).build());
            skills.save(StaffServiceSkill.builder().staff(technician).service(service).build());
            LocalDate date = LocalDate.now(SpaTimeRules.ZONE).plusDays(5);
            var shift = shifts.save(StaffSchedule.builder().staff(technician).workDate(date)
                    .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(18, 0)).build());
            var appointment = Appointment.builder().userId(500L).appointmentDate(date).startTime(LocalTime.of(10, 0))
                    .endTime(LocalTime.of(11, 0)).status(AppointmentStatus.CONFIRMED).items(new ArrayList<>()).build();
            appointment.getItems().add(AppointmentItem.builder().appointment(appointment).staff(technician).service(service)
                    .startTime(appointment.getStartTime()).endTime(appointment.getEndTime()).price(BigDecimal.TEN).build());
            appointments.saveAndFlush(appointment);
            return new Fixture(technician.getId(), service.getId(), shift.getId(), date);
        });
    }
    @Test void shrinkingShiftRollsBackWhenConfirmedAppointmentWouldBeUncovered() {
        var fixture = fixture();
        assertThrows(BusinessException.class, () -> administration.saveSchedule(fixture.staffId(), fixture.shiftId(),
                new AdminStaffService.ScheduleCommand(fixture.date(), LocalTime.of(12, 0), LocalTime.of(18, 0),
                        StaffScheduleStatus.SCHEDULED, "Change shift")));
        tx(() -> {
            assertEquals(LocalTime.of(8, 0), shifts.findById(fixture.shiftId()).orElseThrow().getStartTime());
            assertTrue(shifts.coversWorkingInterval(fixture.staffId(), fixture.date(), LocalTime.of(10, 0), LocalTime.of(11, 0)));
            return null;
        });
    }
    @Test void staffAndSkillCannotBeRemovedWithOutstandingAppointment() {
        var fixture = fixture();
        assertThrows(BusinessException.class, () -> administration.delete(fixture.staffId()));
        assertThrows(BusinessException.class, () -> administration.removeSkill(fixture.staffId(), fixture.serviceId()));
        tx(() -> {
            assertFalse(staff.findById(fixture.staffId()).orElseThrow().getIsDeleted());
            assertTrue(skills.findByStaffIdAndServiceIdAndIsDeletedFalse(fixture.staffId(), fixture.serviceId()).isPresent());
            return null;
        });
    }
    @Test void deletingShiftRollsBackAndKeepsItVisible() {
        var fixture = fixture();
        assertThrows(BusinessException.class, () -> administration.deleteSchedule(fixture.staffId(), fixture.shiftId()));
        tx(() -> { assertFalse(shifts.findById(fixture.shiftId()).orElseThrow().getIsDeleted()); return null; });
        assertEquals(1, administration.schedules(fixture.staffId(), fixture.date(), fixture.date(),
                org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements());
    }
}
