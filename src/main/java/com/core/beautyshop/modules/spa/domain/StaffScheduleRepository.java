package com.core.beautyshop.modules.spa.domain;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.*;

public interface StaffScheduleRepository extends JpaRepository<StaffSchedule, Long> {
    Page<StaffSchedule> findByStaffIdAndWorkDateBetweenAndIsDeletedFalse(Long staffId, LocalDate from, LocalDate to, Pageable pageable);

    @Query("select count(s) > 0 from StaffSchedule s where s.staff.id = :staffId and s.workDate = :date "
            + "and s.status = com.core.beautyshop.modules.spa.domain.enums.StaffScheduleStatus.SCHEDULED "
            + "and s.isDeleted = false and s.startTime <= :startTime and s.endTime >= :endTime")
    boolean coversWorkingInterval(@Param("staffId") Long staffId, @Param("date") LocalDate date,
                                 @Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime);

    @Query("select count(s) > 0 from StaffSchedule s where s.staff.id = :staffId and s.workDate = :date and s.isDeleted = false")
    boolean hasScheduleOnDate(@Param("staffId") Long staffId, @Param("date") LocalDate date);
    @Query("select count(s) > 0 from StaffSchedule s where s.staff.id = :staffId and s.workDate = :date and s.status <> com.core.beautyshop.modules.spa.domain.enums.StaffScheduleStatus.CANCELLED and s.isDeleted = false and s.startTime < :endTime and s.endTime > :startTime")
    boolean existsOverlap(@Param("staffId") Long staffId, @Param("date") LocalDate date, @Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime);

    @Query("select count(s) > 0 from StaffSchedule s where s.staff.id = :staffId and s.id <> :scheduleId and s.workDate = :date and s.status <> com.core.beautyshop.modules.spa.domain.enums.StaffScheduleStatus.CANCELLED and s.isDeleted = false and s.startTime < :endTime and s.endTime > :startTime")
    boolean existsOverlapExcluding(@Param("staffId") Long staffId, @Param("scheduleId") Long scheduleId, @Param("date") LocalDate date, @Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime);
}
