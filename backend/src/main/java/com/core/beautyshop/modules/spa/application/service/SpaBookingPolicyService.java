package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.domain.Appointment;
import com.core.beautyshop.shared.config.domain.SystemConfigRepository;
import com.core.beautyshop.shared.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service @RequiredArgsConstructor
public class SpaBookingPolicyService {
    private final SystemConfigRepository configs;
    private final ObjectMapper json;
    public record Policy(String version, int pendingTtlMinutes, int cancelCutoffMinutes,
                         int rescheduleCutoffMinutes, int noShowGraceMinutes, String noShowQuotaAction) { }
    public Policy current() {
        String noShow = value("spa.booking.no_show_quota_action", "FORFEIT");
        if (!java.util.Set.of("FORFEIT", "RELEASE").contains(noShow)) throw new BusinessException("Quy định xử lý buổi vé khi khách vắng mặt không hợp lệ");
        return new Policy(value("spa.booking.policy_version", "legacy-compatible-v1"),
                minutes("spa.booking.pending_ttl_minutes"), minutes("spa.booking.cancel_cutoff_minutes"),
                minutes("spa.booking.reschedule_cutoff_minutes"), minutes("spa.booking.no_show_grace_minutes"), noShow);
    }
    public String expiryCheckMode() {
        String mode = value("spa.ticket.expiry_check_mode", "BOOKING_TIME");
        if (!java.util.Set.of("BOOKING_TIME", "SERVICE_START").contains(mode)) throw new BusinessException("Cách tính hạn dùng vé liệu trình không hợp lệ");
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
    private int minutes(String key) {
        try {
            int n = Integer.parseInt(value(key, "0"));
            if (n < 0 || n > 525600) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException e) { throw new BusinessException("Cấu hình đặt lịch không hợp lệ: " + key); }
    }
    private String value(String key, String fallback) {
        return configs.findByConfigKeyAndIsDeletedFalse(key).map(c -> c.getConfigValue().trim()).orElse(fallback);
    }
}
