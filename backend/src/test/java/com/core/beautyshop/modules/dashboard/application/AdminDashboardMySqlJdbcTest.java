package com.core.beautyshop.modules.dashboard.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The native DATE_FORMAT/CONVERT_TZ/TIMESTAMPDIFF queries are checked on the production database engine. */
@Testcontainers(disabledWithoutDocker = true)
class AdminDashboardMySqlJdbcTest extends AbstractAdminDashboardJdbcTest {
    @Container static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");
    @Override JdbcTemplate connect() {
        String url = mysql.getJdbcUrl();
        var source = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true", mysql.getUsername(), mysql.getPassword());
        return new JdbcTemplate(source);
    }
    @Override boolean supportsMySqlGrouping() { return true; }
}
