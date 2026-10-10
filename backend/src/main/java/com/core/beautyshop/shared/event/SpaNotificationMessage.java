package com.core.beautyshop.shared.event;

import java.time.LocalDate;
import java.time.LocalTime;

/** References only: private care notes and questionnaire answers never enter this notification payload. */
public record SpaNotificationMessage(Long appointmentId, Kind kind, LocalDate appointmentDate, LocalTime startTime, Long instructionId) {
    public enum Kind { REMINDER, FOLLOW_UP }
}
