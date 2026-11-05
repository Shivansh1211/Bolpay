package com.bolpay.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Security configuration for Spring Security
 * JWT-based stateless authentication
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/api/auth/send-otp", "/api/auth/verify-otp").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/vendor/public/vendor/**").permitAll()
                        .requestMatchers("/v/{slug}").permitAll()
                        // Vendor registration (public for new users)
                        .requestMatchers("/api/vendor/register").permitAll()
                        // Order creation and status (public for customers)
                        .requestMatchers("/api/orders/create").permitAll()
                        .requestMatchers("/api/orders/*/status").permitAll()
                        // SockJS transport is public; the STOMP CONNECT interceptor requires vendor JWT auth.
                        .requestMatchers("/ws/**").permitAll()
                        // Quick check now requires vendor JWT (security fix)
                        .requestMatchers("/api/orders/quick-check/**").authenticated()
                        // Vendor endpoints (require authentication)
                        .requestMatchers("/api/vendor/**").authenticated()
                        // Webhook endpoint (public but will verify signature)
                        .requestMatchers("/api/webhook/**").permitAll()
                        // Health check
                        .requestMatchers("/actuator/health").permitAll()
                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Parse allowed origins from comma-separated string
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // Specific headers instead of wildcard
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "X-Invite-Code",
            "Content-Type",
            "X-Webhook-Signature",
            "X-Webhook-Timestamp",
            "X-Webhook-Version",
            "X-Client-Id",
            "X-Client-Secret",
            "X-Api-Version"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
