package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.domain.Appointment;
import com.core.beautyshop.shared.config.domain.SystemConfigRepository;
import com.core.beautyshop.shared.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Set;

@Service @RequiredArgsConstructor
public class SpaBookingPolicyService {
    private final SystemConfigRepository configs;
    private final ObjectMapper json;
    public record Policy(String version, int pendingTtlMinutes, int cancelCutoffMinutes,
                         int rescheduleCutoffMinutes, int noShowGraceMinutes, String noShowQuotaAction, Integer maxReschedules, Integer maxAdvanceDays) {
        public Policy(String version, int pending, int cancel, int reschedule, int grace, String noShow) {
            this(version, pending, cancel, reschedule, grace, noShow, null, null);
        }
    }
    public Policy current() {
        String noShow = value("spa.booking.no_show_quota_action", "FORFEIT");
        if (!Set.of("FORFEIT", "RELEASE").contains(noShow)) throw new BusinessException("Quy định xử lý buổi vé khi khách vắng mặt không hợp lệ");
        return new Policy(value("spa.booking.policy_version", "spa-v2"),
                minutes("spa.booking.pending_ttl_minutes", 30), minutes("spa.booking.cancel_cutoff_minutes", 120),
                minutes("spa.booking.reschedule_cutoff_minutes", 120), minutes("spa.booking.no_show_grace_minutes", 15), noShow,
                minutes("spa.booking.max_reschedules", 3), minutes("spa.booking.max_advance_days", 90));
    }
    public String expiryCheckMode() {
        String mode = value("spa.ticket.expiry_check_mode", "BOOKING_TIME");
        if (!Set.of("BOOKING_TIME", "SERVICE_START").contains(mode)) throw new BusinessException("Cách tính hạn dùng vé liệu trình không hợp lệ");
        return mode;
    }
    public void snapshot(Appointment appointment) {
        Policy policy = current();
        try { appointment.setPolicySnapshot(json.writeValueAsString(policy)); }
        catch (Exception e) { throw new BusinessException("Không thể lưu thông tin chính sách đặt lịch"); }
        if (policy.pendingTtlMinutes() > 0) appointment.setPendingExpiresAt(Instant.now().plusSeconds(policy.pendingTtlMinutes() * 60L));
    }
    public Policy forAppointment(Appointment appointment) {
        if (appointment.getPolicySnapshot() == null) return new Policy("legacy", 0, 0, 0, 0, "FORFEIT");
        try { return json.readValue(appointment.getPolicySnapshot(), Policy.class); }
        catch (Exception e) { throw new BusinessException("Dữ liệu chính sách đặt lịch không hợp lệ; cần đối soát"); }
    }
    private int minutes(String key, int fallback) {
        try {
            int n = Integer.parseInt(value(key, Integer.toString(fallback)));
            if (n < 0 || n > 525600) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException e) { throw new BusinessException("Cấu hình đặt lịch không hợp lệ: " + key); }
    }
    private String value(String key, String fallback) {
        return configs.findByConfigKeyAndIsDeletedFalse(key).map(c -> c.getConfigValue().trim()).orElse(fallback);
    }
}
