package com.core.beautyshop.modules.spa.domain;

import com.core.beautyshop.modules.spa.domain.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByUserId(Long userId);

    @Query("SELECT COUNT(ai) > 0 FROM AppointmentItem ai " +
           "WHERE ai.staff.id = :staffId " +
           "AND ai.appointment.appointmentDate = :appointmentDate " +
           "AND ai.appointment.status <> com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.CANCELLED " +
           "AND ai.startTime < :endTime " +
           "AND ai.endTime > :startTime")
    boolean existsOverlappingAppointmentForStaff(
            @Param("staffId") Long staffId,
            @Param("appointmentDate") LocalDate appointmentDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );
}
