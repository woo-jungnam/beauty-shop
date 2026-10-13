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
    @Query("select count(i) > 0 from AppointmentItem i where i.isDeleted = false and i.appointment.isDeleted = false "
            + "and i.staff.id = :staffId and i.id <> :itemId and i.executionStatus = "
            + "com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.IN_PROGRESS "
            + "and i.appointment.status = com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.IN_PROGRESS")
    boolean hasActiveExecutionForStaff(@Param("staffId") Long staffId, @Param("itemId") Long itemId);
    @Query("select a from Appointment a where a.isDeleted = false and (:date is null or a.appointmentDate = :date) "
            + "and (:status is null or a.status = :status) order by a.appointmentDate desc, a.startTime desc")
    org.springframework.data.domain.Page<Appointment> findManagedAppointments(@Param("date") LocalDate date,
            @Param("status") com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus status, org.springframework.data.domain.Pageable pageable);

    @Query("select a from Appointment a where a.isDeleted = false and (:date is null or a.appointmentDate = :date) "
            + "and (:status is null or a.status = :status) and exists (select i.id from AppointmentItem i "
            + "where i.appointment = a and i.isDeleted = false and i.staff.isDeleted = false and i.staff.userId = :userId) "
            + "order by a.appointmentDate desc, a.startTime desc")
    org.springframework.data.domain.Page<Appointment> findAssignedAppointments(@Param("userId") Long userId,
            @Param("date") LocalDate date, @Param("status") com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus status,
            org.springframework.data.domain.Pageable pageable);

    @Query("select a.id from Appointment a where a.isDeleted = false and a.id > :cursor and a.status = "
            + "com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.PENDING "
            + "and a.pendingExpiresAt <= :now order by a.id")
    List<Long> findExpiredPendingIds(@Param("cursor") Long cursor, @Param("now") java.time.Instant now, org.springframework.data.domain.Pageable pageable);
    @Query("select count(ai) > 0 from AppointmentItem ai join ai.appointment a "
            + "where ai.service.id = :serviceId and ai.isDeleted = false and a.isDeleted = false and ai.executionStatus not in (com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.SKIPPED, com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.NO_SHOW) "
            + "and a.status in (com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.PENDING, "
            + "com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.CONFIRMED, "
            + "com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.IN_PROGRESS)")
    boolean hasOutstandingServiceAppointments(@Param("serviceId") Long serviceId);

    @Query("select ai from AppointmentItem ai join fetch ai.appointment a "
            + "where ai.staff.id = :staffId and ai.isDeleted = false and a.isDeleted = false and ai.executionStatus not in (com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.SKIPPED, com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.NO_SHOW) "
            + "and a.appointmentDate >= :fromDate and a.status in ("
            + "com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.PENDING, "
            + "com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.CONFIRMED, "
            + "com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.IN_PROGRESS)")
    List<AppointmentItem> findFutureAssignedItems(@Param("staffId") Long staffId, @Param("fromDate") LocalDate fromDate);

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
           "AND ai.isDeleted = false AND ai.appointment.isDeleted = false "+
           "AND ai.executionStatus not in (com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.SKIPPED, com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.NO_SHOW) "+
           "AND ai.appointment.status in (com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.PENDING, com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.CONFIRMED, com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.IN_PROGRESS) " +
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
           "AND ai.isDeleted = false AND ai.appointment.isDeleted = false "+
           "AND ai.executionStatus not in (com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.SKIPPED, com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.NO_SHOW) "+
           "AND ai.appointment.status in (com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.PENDING, com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.CONFIRMED, com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.IN_PROGRESS) " +
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
           "AND ai.isDeleted = false AND ai.appointment.isDeleted = false "+
           "AND ai.executionStatus not in (com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.SKIPPED, com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.NO_SHOW) "+
           "AND ai.appointment.status in (com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.PENDING, com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.CONFIRMED, com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.IN_PROGRESS) " +
           "AND ai.startTime < :endTime " +
           "AND ai.endTime > :startTime")
    boolean existsOverlappingAppointmentForStaffExcludingAppointment(
            @Param("staffId") Long staffId,
            @Param("appointmentDate") LocalDate appointmentDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("appointmentId") Long appointmentId
    );

    @Query("SELECT COUNT(a) > 0 FROM Appointment a " +
           "WHERE a.userId = :userId " +
           "AND a.appointmentDate = :appointmentDate " +
           "AND a.isDeleted = false " +
           "AND a.status IN (com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.PENDING, " +
           "com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.CONFIRMED, " +
           "com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.IN_PROGRESS) " +
           "AND a.startTime < :endTime " +
           "AND a.endTime > :startTime")
    boolean existsOverlappingAppointmentForUser(
            @Param("userId") Long userId,
            @Param("appointmentDate") LocalDate appointmentDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );

    @Query("SELECT COUNT(a) > 0 FROM Appointment a " +
           "WHERE a.userId = :userId " +
           "AND a.id <> :appointmentId " +
           "AND a.appointmentDate = :appointmentDate " +
           "AND a.isDeleted = false " +
           "AND a.status IN (com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.PENDING, " +
           "com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.CONFIRMED, " +
           "com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.IN_PROGRESS) " +
           "AND a.startTime < :endTime " +
           "AND a.endTime > :startTime")
    boolean existsOverlappingAppointmentForUserExcluding(
            @Param("userId") Long userId,
            @Param("appointmentId") Long appointmentId,
            @Param("appointmentDate") LocalDate appointmentDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT ai FROM AppointmentItem ai " +
           "WHERE ai.staff.id = :staffId " +
           "AND ai.appointment.id <> :appointmentId " +
           "AND ai.appointment.appointmentDate = :appointmentDate " +
           "AND ai.isDeleted = false AND ai.appointment.isDeleted = false "+
           "AND ai.executionStatus not in (com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.SKIPPED, com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.NO_SHOW) "+
           "AND ai.appointment.status in (com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.PENDING, com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.CONFIRMED, com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.IN_PROGRESS) " +
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
