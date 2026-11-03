package com.bolpay.util;

import org.springframework.web.util.HtmlUtils;

/**
 * Utility class for sanitizing user input to prevent XSS
 */
public class Sanitizer {

    /**
     * Sanitize text by escaping HTML entities
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        // Escape HTML entities
        String sanitized = HtmlUtils.htmlEscape(input);
        // Remove any remaining potentially dangerous characters
        sanitized = sanitized.replaceAll("[<>\"'&]", "");
        return sanitized;
    }

    /**
     * Sanitize and truncate text to max length
     */
    public static String sanitize(String input, int maxLength) {
        String sanitized = sanitize(input);
        if (sanitized != null && sanitized.length() > maxLength) {
            return sanitized.substring(0, maxLength);
        }
        return sanitized;
    }
}
