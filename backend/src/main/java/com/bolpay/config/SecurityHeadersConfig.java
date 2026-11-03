package com.bolpay.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Configuration for security headers
 */
@Configuration
public class SecurityHeadersConfig {

    @Bean
    public HeaderWriterFilter securityHeadersFilter() {
        return new HeaderWriterFilter((request, response) -> {
            // Content Security Policy
            response.setHeader("Content-Security-Policy",
                "default-src 'self'; " +
                "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                "style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data: https:; " +
                "font-src 'self' data:; " +
                "connect-src 'self' ws://localhost:8080 http://localhost:8080; " +
                "frame-ancestors 'none';"
            );
            
            // Prevent clickjacking
            response.setHeader("X-Frame-Options", "DENY");
            
            // Prevent MIME type sniffing
            response.setHeader("X-Content-Type-Options", "nosniff");
            
            // Enable XSS protection
            response.setHeader("X-XSS-Protection", "1; mode=block");
            
            // Referrer policy
            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            
            // Permissions policy
            response.setHeader("Permissions-Policy",
                "geolocation=(), " +
                "microphone=(), " +
                "camera=(), " +
                "payment=()"
            );
            
            // HSTS (only in production with HTTPS)
            // response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        });
    }
}
