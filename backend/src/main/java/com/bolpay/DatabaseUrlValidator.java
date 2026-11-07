package com.bolpay;

/** Validates the external datasource URL without ever including its value in an error. */
public final class DatabaseUrlValidator {
    private static final String POSTGRES_JDBC_PREFIX = "jdbc:postgresql://";

    private DatabaseUrlValidator() {}

    public static void validate(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "Database configuration error: SPRING_DATASOURCE_URL is required.");
        }
        if (!url.startsWith(POSTGRES_JDBC_PREFIX)) {
            throw new IllegalStateException(
                    "Database configuration error: SPRING_DATASOURCE_URL must start with jdbc:postgresql://.");
        }
        String authority = url.substring(POSTGRES_JDBC_PREFIX.length()).split("[/ ?#]", 2)[0];
        String query = url.contains("?") ? url.substring(url.indexOf('?') + 1) : "";
        if (authority.contains("@") || query.matches("(?is).*(?:^|&)(?:user|password)=[^&]*(?:&|$).*")) {
            throw new IllegalStateException(
                    "Database configuration error: keep database username and password in their separate environment variables.");
        }
    }
}
