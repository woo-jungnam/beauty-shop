package com.core.beautyshop.shared.config;

import com.core.beautyshop.shared.config.domain.SystemConfigRepository;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:config_fix;DB_CLOSE_DELAY=-1;MODE=MySQL")
@ActiveProfiles("test")
class SystemConfigIntegrationTest {
    @Autowired SystemConfigService configs;
    @Autowired SystemConfigRepository repository;
    @Autowired com.core.beautyshop.modules.spa.application.service.SpaBookingPolicyService policies;
    @org.junit.jupiter.api.BeforeEach void clearConfigs() { repository.deleteAll(); }
    @Test void bookingRulesRejectInvalidTypesBoundsAndUnsupportedActions() {
        assertThrows(BusinessException.class, () -> configs.create("spa.booking.pending_ttl_minutes", "-1", null, null));
        assertThrows(BusinessException.class, () -> configs.create("spa.booking.cancel_cutoff_minutes", "30", null, "STRING"));
        assertThrows(BusinessException.class, () -> configs.create("spa.booking.no_show_grace_minutes", "525601", null, null));
        assertThrows(BusinessException.class, () -> configs.create("spa.booking.no_show_quota_action", "CHARGE_FEE", null, null));
        assertThrows(BusinessException.class, () -> configs.create("spa.ticket.expiry_check_mode", "NOW", null, null));
        assertEquals(0, repository.count());
    }
    @Test void changingConfigDoesNotReinterpretBookedPolicyOrLegacyAppointments() {
        configs.create("spa.booking.policy_version", "spa-v2", null, null);
        configs.create("spa.booking.pending_ttl_minutes", "15", null, null);
        configs.create("spa.booking.cancel_cutoff_minutes", "60", null, null);
        configs.create("spa.booking.no_show_quota_action", "RELEASE", null, null);
        var appointment = new com.core.beautyshop.modules.spa.domain.Appointment();
        policies.snapshot(appointment); String snapshot = appointment.getPolicySnapshot();
        assertNotNull(appointment.getPendingExpiresAt());
        configs.update("spa.booking.cancel_cutoff_minutes", "120"); configs.update("spa.booking.no_show_quota_action", "FORFEIT");
        assertEquals(120, policies.current().cancelCutoffMinutes());
        assertEquals(60, policies.forAppointment(appointment).cancelCutoffMinutes());
        assertEquals("RELEASE", policies.forAppointment(appointment).noShowQuotaAction()); assertEquals(snapshot, appointment.getPolicySnapshot());
        assertEquals("FORFEIT", policies.forAppointment(new com.core.beautyshop.modules.spa.domain.Appointment()).noShowQuotaAction());
    }


    @Test
    void validatesTypedValuesRestoresDeletedKeysAndReadsNewExpiryThreshold() {
        assertEquals(30, configs.expiryWarningDays());
        var first = configs.create("inventory.expiry_warning_days", "7", "Expiry", null);
        assertEquals("INTEGER", first.getValueType());
        assertEquals(7, configs.expiryWarningDays());
        assertThrows(BusinessException.class, () -> configs.update(first.getConfigKey(), "-1"));
        assertThrows(BusinessException.class, () -> configs.update(first.getConfigKey(), "1.5"));
        assertThrows(BusinessException.class, () -> configs.update(first.getConfigKey(), "not-a-number"));
        assertEquals(7, configs.expiryWarningDays());
        configs.delete(first.getConfigKey());
        assertEquals(30, configs.expiryWarningDays());
        var restored = configs.create(first.getConfigKey(), "14", "Restored", null);
        assertEquals(first.getId(), restored.getId());
        assertEquals(14, configs.expiryWarningDays());
        assertEquals(1, repository.count());
        assertThrows(BusinessException.class, () -> configs.create("dashboard.monthly_order_target", "-5", null, null));
        assertThrows(BusinessException.class, () -> configs.create("dashboard.monthly_revenue_target", "-0.5", null, null));
    }
}
