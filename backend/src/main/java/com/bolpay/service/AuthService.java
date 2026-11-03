package com.bolpay.service;

import com.bolpay.dto.AuthResponse;
import com.bolpay.dto.VendorDto;
import com.bolpay.entity.Vendor;
import com.bolpay.repository.VendorRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for authentication and JWT token management
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final VendorRepository vendorRepository;
    private final OtpService otpService;
    private final VendorService vendorService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpirationMs;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * Generate JWT token for a vendor
     */
    public String generateToken(String phoneNumber, Long vendorId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("phoneNumber", phoneNumber);
        claims.put("vendorId", vendorId);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(phoneNumber)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Validate JWT token
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract phone number from JWT token
     */
    public String extractPhoneNumber(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getSubject();
    }

    /**
     * Extract vendor ID from JWT token
     */
    public Long extractVendorId(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.get("vendorId", Long.class);
    }

    /**
     * Extract vendor ID from Spring Security Authentication object
     */
    public Long extractVendorIdFromAuthentication(org.springframework.security.core.Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("User not authenticated");
        }
        String phoneNumber = authentication.getName();
        // Need to look up vendor by phone number since JWT claims aren't directly accessible
        Vendor vendor = vendorRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found"));
        return vendor.getId();
    }

    /**
     * Send OTP and return the OTP for demo purposes
     */
    public String sendOtp(String phoneNumber) {
        return otpService.generateAndSendOtp(phoneNumber);
    }

    /**
     * Verify OTP and return auth response with JWT token
     */
    public AuthResponse verifyOtp(String phoneNumber, String otpCode) {
        if (!otpService.verifyOtp(phoneNumber, otpCode)) {
            throw new IllegalArgumentException("Invalid OTP");
        }

        Vendor vendor = vendorRepository.findByPhoneNumber(phoneNumber)
                .orElse(null);
        boolean isNewUser = (vendor == null);

        String token;
        VendorDto vendorDto;

        if (isNewUser) {
            // New user - return token without vendor data
            // They will need to register
            token = generateToken(phoneNumber, null);
            vendorDto = null;
        } else {
            // Existing user - return token with vendor data
            token = generateToken(phoneNumber, vendor.getId());
            vendorDto = vendorService.getVendorByPhoneNumber(phoneNumber);
        }

        return AuthResponse.builder()
                .token(token)
                .phoneNumber(phoneNumber)
                .isNewUser(isNewUser)
                .vendor(vendorDto)
                .build();
    }
}
