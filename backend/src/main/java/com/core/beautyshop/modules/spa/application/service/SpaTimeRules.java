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
            throw new BusinessException("Thời lượng dịch vụ Spa hoặc thời gian chuẩn bị không hợp lệ");
        }
        long total = (long) duration + preparation;
        if (total > Duration.between(OPEN, CLOSE).toMinutes()) {
            throw new BusinessException("Thời lượng dịch vụ vượt quá khung giờ mở cửa");
        }
        return total;
    }

    public static LocalTime end(LocalTime start, BeautyService service) {
        long duration = durationMinutes(service);
        if (start == null || duration >= Duration.between(start, LocalTime.MAX).toMinutes()+1) {
            throw new BusinessException("Dịch vụ phải kết thúc trong cùng ngày");
        }
        return start.plusMinutes(duration);
    }

    public static void requireFuture(LocalDate date, LocalTime start) {
        if (date == null || start == null || date.atTime(start).isBefore(LocalDateTime.now(ZONE).minusMinutes(5))) {
            throw new BusinessException("Ngày và giờ hẹn phải ở trong tương lai");
        }
    }
}
