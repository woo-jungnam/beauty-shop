package com.core.beautyshop.modules.spa.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @EntityGraph(attributePaths = {"items", "items.service", "items.staff", "items.ticket"})
    List<Appointment> findByUserIdOrderByAppointmentDateDescStartTimeDesc(Long userId);

    @EntityGraph(attributePaths = {"items", "items.service", "items.staff", "items.ticket"})
    List<Appointment> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Appointment a WHERE a.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"items", "items.service", "items.staff", "items.ticket"})
    @Query("SELECT a FROM Appointment a WHERE a.id = :id")
    Optional<Appointment> findByIdWithItems(@Param("id") Long id);

    org.springframework.data.domain.Page<Appointment> findAllByOrderByAppointmentDateDescStartTimeDesc(org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<Appointment> findByAppointmentDateOrderByStartTimeAsc(LocalDate date, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<Appointment> findByStatusOrderByAppointmentDateDescStartTimeDesc(com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus status, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<Appointment> findByAppointmentDateAndStatusOrderByStartTimeAsc(LocalDate date, com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus status, org.springframework.data.domain.Pageable pageable);

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

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT ai FROM AppointmentItem ai " +
           "WHERE ai.staff.id = :staffId " +
           "AND ai.appointment.appointmentDate = :appointmentDate " +
           "AND ai.appointment.status <> com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.CANCELLED " +
           "AND ai.startTime < :endTime " +
           "AND ai.endTime > :startTime")
    List<AppointmentItem> findOverlappingAppointmentsForStaffWithLock(
            @Param("staffId") Long staffId,
            @Param("appointmentDate") LocalDate appointmentDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );

    @Query("SELECT COUNT(ai) > 0 FROM AppointmentItem ai " +
           "WHERE ai.staff.id = :staffId " +
           "AND ai.appointment.id <> :appointmentId " +
           "AND ai.appointment.appointmentDate = :appointmentDate " +
           "AND ai.appointment.status <> com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.CANCELLED " +
           "AND ai.startTime < :endTime " +
           "AND ai.endTime > :startTime")
    boolean existsOverlappingAppointmentForStaffExcludingAppointment(
            @Param("staffId") Long staffId,
            @Param("appointmentDate") LocalDate appointmentDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("appointmentId") Long appointmentId
    );

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT ai FROM AppointmentItem ai " +
           "WHERE ai.staff.id = :staffId " +
           "AND ai.appointment.id <> :appointmentId " +
           "AND ai.appointment.appointmentDate = :appointmentDate " +
           "AND ai.appointment.status <> com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.CANCELLED " +
           "AND ai.startTime < :endTime " +
           "AND ai.endTime > :startTime")
    List<AppointmentItem> findOverlappingAppointmentsForStaffExcludingAppointmentWithLock(
            @Param("staffId") Long staffId,
            @Param("appointmentDate") LocalDate appointmentDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("appointmentId") Long appointmentId
    );
}
