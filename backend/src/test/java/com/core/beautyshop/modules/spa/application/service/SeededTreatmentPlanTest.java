package com.core.beautyshop.modules.spa.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.core.beautyshop.modules.spa.application.service.TreatmentContracts.Plan;
import static org.junit.jupiter.api.Assertions.*;

class SeededTreatmentPlanTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void allSeededTreatmentPlansParseIntoValidPlanContracts() throws Exception {
        ClassPathResource resource = new ClassPathResource("db/migration/V69__seed_authentic_intensive_treatment_courses.sql");
        String sql = resource.getContentAsString(StandardCharsets.UTF_8);

        Pattern pattern = Pattern.compile("'(\\{\"name\":.+?\\})'", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(sql);

        int count = 0;
        while (matcher.find()) {
            count++;
            String jsonStr = matcher.group(1).replace("\\n", "\n");
            Plan plan = objectMapper.readValue(jsonStr, Plan.class);

            assertNotNull(plan.name());
            assertNotNull(plan.serviceId());
            assertNotNull(plan.price());
            assertTrue(plan.price().signum() > 0);
            assertNotNull(plan.terms());
            assertFalse(plan.sessions().isEmpty());

            int expectedSeq = 1;
            for (var session : plan.sessions()) {
                assertEquals(expectedSeq++, session.sequence());
                assertNotNull(session.title());
                assertTrue(session.minGapDays() >= 0);
                assertFalse(session.items().isEmpty());

                for (var item : session.items()) {
                    assertNotNull(item.serviceId());
                    assertNotNull(item.serviceName());
                    assertTrue(item.required());
                }
            }
        }

        assertEquals(4, count, "Must find and validate exactly 4 authentic clinical treatment plans");
    }
}
