package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.domain.BeautyService;
import com.core.beautyshop.shared.exception.BusinessException;
import java.time.*;

/** Shared business time rules. Do not use the server's default timezone for bookings. */
public final class SpaTimeRules {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final LocalTime OPEN = LocalTime.of(8, 0);
    public static final LocalTime CLOSE = LocalTime.of(20, 0);

    private SpaTimeRules() { }

    public static long durationMinutes(BeautyService service) {
        Integer duration = service.getDurationMinutes();
        int preparation = service.getPreparationTimeMinutes() == null ? 0 : service.getPreparationTimeMinutes();
        if (duration == null || duration <= 0 || preparation < 0) {
            throw new BusinessException("Invalid Spa service duration or preparation time");
        }
        long total = (long) duration + preparation;
        if (total > Duration.between(OPEN, CLOSE).toMinutes()) {
            throw new BusinessException("Service duration exceeds opening hours");
        }
        return total;
    }

    public static LocalTime end(LocalTime start, BeautyService service) {
        long duration = durationMinutes(service);
        if (start == null || start.isBefore(OPEN) || !start.isBefore(CLOSE)
                || duration > Duration.between(start, CLOSE).toMinutes()) {
            throw new BusinessException("Service must finish within opening hours");
        }
        return start.plusMinutes(duration);
    }

    public static void requireFuture(LocalDate date, LocalTime start) {
        if (date == null || start == null || date.atTime(start).isBefore(LocalDateTime.now(ZONE).minusMinutes(5))) {
            throw new BusinessException("Appointment date and time must be in the future");
        }
    }
}
