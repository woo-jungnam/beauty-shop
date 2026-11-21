package com.core.beautyshop;

import com.core.beautyshop.modules.spa.domain.*;
import java.io.File;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.cors.allowed-origins=http://127.0.0.1:15973")
@EnabledIfEnvironmentVariable(named = "RUN_SPA_BROWSER", matches = "true")
class SpaPackageBrowserIntegrationTest extends SpaWorkflowTestSupport {
    @LocalServerPort int port;
    @Autowired PasswordEncoder encoder;

    @Test void customerBooksTwoBenefitsThroughStorefront() throws Exception {
        var f = fixture();
        var password = UUID.randomUUID().toString();
        tx(() -> {
            var user = users.findById(f.customer().getId()).orElseThrow();
            user.setPasswordHash(encoder.encode(password));
            user.setPhone("0987654321");
            var second = services.save(BeautyService.builder().name("Second benefit").slug(UUID.randomUUID().toString())
                    .basePrice(BigDecimal.TEN).durationMinutes(30).build());
            skills.save(StaffServiceSkill.builder().staff(staffs.getReferenceById(f.firstStaffId())).service(second).isCertified(true).build());
            var ticket = tickets.findById(f.ticketId()).orElseThrow();
            ticket.setTotalSessions(2);
            ticket.getEntitlements().put(second.getId(), new TicketEntitlement(1, 0));
            ticket.getServiceNames().put(second.getId(), second.getName());
            return null;
        });
        var builder = new ProcessBuilder("node", "e2e/spa-package-live.mjs").directory(new File("../frontend")).inheritIO();
        builder.environment().put("SPA_TEST_PORT", Integer.toString(port));
        builder.environment().put("SPA_TEST_USERNAME", f.customer().getUsername());
        builder.environment().put("SPA_TEST_PASSWORD", password);
        builder.environment().put("SPA_TEST_DATE", f.date().toString());
        builder.environment().put("SPA_TEST_SERVICE", Long.toString(f.serviceId()));
        builder.environment().put("SPA_TEST_TICKET", Long.toString(f.ticketId()));
        var process = builder.start();
        try {
            assertTrue(process.waitFor(120, TimeUnit.SECONDS), "Browser timed out");
            assertEquals(0, process.exitValue());
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
        assertEquals(2, tickets.findById(f.ticketId()).orElseThrow().getReservedSessions());
    }
}
