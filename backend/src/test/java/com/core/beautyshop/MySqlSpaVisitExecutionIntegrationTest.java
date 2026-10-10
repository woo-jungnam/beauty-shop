package com.core.beautyshop;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker=true)
class MySqlSpaVisitExecutionIntegrationTest extends SpaVisitExecutionIntegrationTest {
    @Container static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withCommand("--log-bin-trust-function-creators=1", "--default-time-zone=+00:00");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",mysql::getJdbcUrl);
        p.add("spring.datasource.username",mysql::getUsername); p.add("spring.datasource.password",mysql::getPassword);
        p.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
        p.add("spring.datasource.hikari.connection-init-sql",()->"SET time_zone = '+00:00'");
        p.add("spring.datasource.hikari.transaction-isolation",()->"TRANSACTION_READ_COMMITTED");
        p.add("spring.jpa.database-platform",()->"org.hibernate.dialect.MySQLDialect");
        p.add("spring.jpa.properties.hibernate.dialect",()->"org.hibernate.dialect.MySQLDialect");
        p.add("spring.jpa.hibernate.ddl-auto",()->"validate"); p.add("spring.flyway.enabled",()->"true");
    }
}
