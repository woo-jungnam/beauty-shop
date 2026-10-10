package com.core.beautyshop.shared.config;

import com.core.beautyshop.shared.config.api.AdminOperationalAlertController;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminOperationalAlertControllerTest {
    @Test
    void expirySqlAndTitleUseTheConfiguredDays() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SystemConfigService configs = mock(SystemConfigService.class);
        when(configs.expiryWarningDays()).thenReturn(7);
        when(jdbc.queryForObject(contains("expiration_date"), eq(Long.class), eq(7))).thenReturn(2L);
        var result = new AdminOperationalAlertController(jdbc, configs).alerts().getData();
        var expiry = result.stream().filter(alert -> "EXPIRY".equals(alert.code())).findFirst().orElseThrow();
        assertEquals("Lô hết hạn trong 7 ngày", expiry.title());
        assertEquals(2, expiry.count());
        verify(jdbc).queryForObject(contains("INTERVAL ? DAY"), eq(Long.class), eq(7));
    }
}
