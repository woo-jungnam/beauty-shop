package com.core.beautyshop.modules.spa.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @EntityGraph(attributePaths = {"items", "items.service", "items.staff", "items.ticket"})
    List<Appointment> findByUserIdOrderByAppointmentDateDescStartTimeDesc(Long userId);

    @EntityGraph(attributePaths = {"items", "items.service", "items.staff", "items.ticket"})
    List<Appointment> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"items", "items.service", "items.staff", "items.ticket"})
    @Query("SELECT a FROM Appointment a WHERE a.id = :id")
    Optional<Appointment> findByIdWithItems(@Param("id") Long id);

    @EntityGraph(attributePaths = {"items", "items.service", "items.staff", "items.ticket"})
    org.springframework.data.domain.Page<Appointment> findAllByOrderByAppointmentDateDescStartTimeDesc(org.springframework.data.domain.Pageable pageable);

    @EntityGraph(attributePaths = {"items", "items.service", "items.staff", "items.ticket"})
    org.springframework.data.domain.Page<Appointment> findByAppointmentDateOrderByStartTimeAsc(LocalDate date, org.springframework.data.domain.Pageable pageable);

    @EntityGraph(attributePaths = {"items", "items.service", "items.staff", "items.ticket"})
    org.springframework.data.domain.Page<Appointment> findByStatusOrderByAppointmentDateDescStartTimeDesc(com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus status, org.springframework.data.domain.Pageable pageable);

    @EntityGraph(attributePaths = {"items", "items.service", "items.staff", "items.ticket"})
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
}
