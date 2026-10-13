package com.core.beautyshop.shared.security.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtSecretValidationTest {
    @Test
    void refusesMissingWeakAndKnownDefaultKeysWithoutPrintingThem() {
        JwtUtils jwt = new JwtUtils();
        for (String secret : new String[]{"", "short", "beautyshop_jwt_secret_key_must_be_at_least_256_bits_long_1234567890!"}) {
            ReflectionTestUtils.setField(jwt, "jwtSecret", secret);
            assertThrows(IllegalStateException.class, jwt::validateSecret);
        }
        ReflectionTestUtils.setField(jwt, "jwtSecret", "test_profile_secret_with_at_least_32_bytes_12345");
        assertDoesNotThrow(jwt::validateSecret);
    }
}
