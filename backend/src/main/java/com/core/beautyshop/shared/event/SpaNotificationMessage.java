package com.core.beautyshop.shared.event;

import java.time.LocalDate;
import java.time.LocalTime;

public record SpaNotificationMessage(Long appointmentId, Kind kind, LocalDate appointmentDate, LocalTime startTime, Long instructionId) {
    public enum Kind { REMINDER, FOLLOW_UP }
}
