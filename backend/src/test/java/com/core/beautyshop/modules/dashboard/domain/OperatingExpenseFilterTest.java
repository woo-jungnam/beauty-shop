package com.core.beautyshop.modules.dashboard.domain;

import com.core.beautyshop.modules.dashboard.application.ProfitabilityService;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ActiveProfiles("test")
class OperatingExpenseFilterTest {
    @Autowired
    OperatingExpenseRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void filtersActiveExpensesByCategoryAndIncurredRange() {
        save("EXP-1", "RENT", "Mat bang T10", "5000000", "2026-10-05T00:00:00Z", false);
        save("EXP-2", "OPERATING", "Dien nuoc T10", "800000", "2026-10-15T00:00:00Z", false);
        save("EXP-3", "RENT", "Mat bang T11", "5000000", "2026-11-05T00:00:00Z", false);
        save("EXP-DEL", "RENT", "Phieu da xoa", "100000", "2026-10-10T00:00:00Z", true);
        repository.flush();

        var service = new ProfitabilityService(jdbc, repository);
        var pageable = PageRequest.of(0, 20);

        assertEquals(3, service.list(pageable).getTotalElements());
        assertEquals(2, service.list(null, null, "RENT", pageable).getTotalElements());

        var octoberRent = service.list(
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-11-01T00:00:00Z"),
                "rent",
                pageable
        );
        assertEquals(1, octoberRent.getTotalElements());
        assertEquals("EXP-1", octoberRent.getContent().getFirst().getReference());

        assertThrows(BusinessException.class, () -> service.list(
                Instant.parse("2026-11-01T00:00:00Z"),
                Instant.parse("2026-10-01T00:00:00Z"),
                null,
                pageable
        ));
    }

    private void save(String ref, String category, String description, String amount, String incurredAt, boolean deleted) {
        OperatingExpense row = new OperatingExpense();
        Instant now = Instant.now();
        row.setReference(ref);
        row.setCategory(category);
        row.setDescription(description);
        row.setAmount(new BigDecimal(amount));
        row.setIncurredAt(Instant.parse(incurredAt));
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        row.setIsDeleted(deleted);
        repository.save(row);
    }
}

