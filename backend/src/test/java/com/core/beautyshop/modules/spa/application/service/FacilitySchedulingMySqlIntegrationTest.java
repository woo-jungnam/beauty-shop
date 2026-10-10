package com.core.beautyshop.modules.spa.application.service;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class FacilitySchedulingMySqlIntegrationTest extends FacilitySchedulingIntegrationTest {
    @Container static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withCommand("--log-bin-trust-function-creators=1", "--default-time-zone=+00:00");
    @DynamicPropertySource static void mysqlProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", mysql::getJdbcUrl);
        properties.add("spring.datasource.username", mysql::getUsername);
        properties.add("spring.datasource.password", mysql::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "SET time_zone = '+00:00'");
        properties.add("spring.datasource.hikari.transaction-isolation", () -> "TRANSACTION_READ_COMMITTED");
        properties.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
        properties.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.flyway.enabled", () -> "true");
    }
}
