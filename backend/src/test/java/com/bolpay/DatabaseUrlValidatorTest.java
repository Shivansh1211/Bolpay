package com.bolpay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseUrlValidatorTest {
    @Test
    void acceptsJdbcPostgresUrlWithoutLoggingItsValue() {
        assertDoesNotThrow(() -> DatabaseUrlValidator.validate(
                "jdbc:postgresql://db.example.invalid/bolpay?sslmode=require&channelBinding=require"));
    }

    @Test
    void rejectsMissingUrlWithClearMessage() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DatabaseUrlValidator.validate(null));
        assertTrue(error.getMessage().contains("SPRING_DATASOURCE_URL is required"));
    }

    @Test
    void rejectsUnsupportedUrlWithoutEchoingIt() {
        String invalid = "postgresql://fictional-secret.example/db";
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DatabaseUrlValidator.validate(invalid));
        assertTrue(error.getMessage().contains("must start with jdbc:postgresql://"));
        assertTrue(!error.getMessage().contains(invalid));
    }

    @Test
    void rejectsCredentialsEmbeddedInUrl() {
        String invalid = "jdbc:postgresql://user:password@db.example.invalid/bolpay?sslmode=require";
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DatabaseUrlValidator.validate(invalid));
        assertTrue(error.getMessage().contains("separate environment variables"));
        assertTrue(!error.getMessage().contains(invalid));
    }
}
