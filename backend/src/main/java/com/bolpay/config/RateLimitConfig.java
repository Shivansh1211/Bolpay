package com.bolpay.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting configuration using Bucket4j
 * Limits OTP and order creation requests per IP address
 */
@Component
public class RateLimitConfig implements HandlerInterceptor {

    private final Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
    
    // OTP: 5 requests per hour per IP
    private static final int OTP_LIMIT = 5;
    private static final Duration OTP_DURATION = Duration.ofHours(1);
    
    // Order creation: 20 requests per minute per IP
    private static final int ORDER_LIMIT = 20;
    private static final Duration ORDER_DURATION = Duration.ofMinutes(1);

    private Bucket resolveBucket(String key, int limit, Duration duration) {
        return ipBuckets.computeIfAbsent(key, k -> {
            Bandwidth limitBandwidth = Bandwidth.classic(limit, Refill.greedy(limit, duration));
            return Bucket4j.builder().addLimit(limitBandwidth).build();
        });
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String ip = getClientIp(request);
        String path = request.getRequestURI();
        
        Bucket bucket = null;
        
        if (path.contains("/auth/send-otp")) {
            bucket = resolveBucket("otp:" + ip, OTP_LIMIT, OTP_DURATION);
        } else if (path.contains("/orders/create")) {
            bucket = resolveBucket("order:" + ip, ORDER_LIMIT, ORDER_DURATION);
        }
        
        if (bucket != null) {
            if (bucket.tryConsume(1)) {
                return true;
            } else {
                response.setStatus(429); // Too Many Requests
                response.getWriter().write("{\"error\":\"Rate limit exceeded\"}");
                return false;
            }
        }
        
        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // Handle multiple IPs in X-Forwarded-For
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
