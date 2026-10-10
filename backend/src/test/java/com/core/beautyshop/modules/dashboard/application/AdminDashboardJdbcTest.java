package com.core.beautyshop.modules.dashboard.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;

class AdminDashboardJdbcTest extends AbstractAdminDashboardJdbcTest {
    @Override JdbcTemplate connect() {
        return new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:dashboard_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
    }
    @Override boolean supportsMySqlGrouping() { return false; }
}
