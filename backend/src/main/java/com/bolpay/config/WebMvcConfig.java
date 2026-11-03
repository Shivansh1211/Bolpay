package com.bolpay.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration for interceptors
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitConfig rateLimitConfig;

    public WebMvcConfig(RateLimitConfig rateLimitConfig) {
        this.rateLimitConfig = rateLimitConfig;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitConfig)
                .addPathPatterns("/api/auth/send-otp", "/api/auth/verify-otp", "/api/orders/create");
    }
}
