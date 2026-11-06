package com.bolpay.controller;

import com.bolpay.dto.RegisterVendorRequest;
import com.bolpay.dto.VendorDto;
import com.bolpay.dto.PublicVendorDto;
import com.bolpay.service.AuthService;
import com.bolpay.service.VendorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Controller for vendor endpoints
 */
@RestController
@RequestMapping("/api/vendor")
@RequiredArgsConstructor
@Slf4j
public class VendorController {

    private final VendorService vendorService;
    private final AuthService authService;

    @Value("${registration.invite-code:}")
    private String inviteCode;

    /**
     * Register a new vendor
     * POST /api/vendor/register
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerVendor(@Valid @RequestBody RegisterVendorRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Invite-Code", required = false) String suppliedCode) {
        if (authorization == null || !authorization.startsWith("Bearer ")
                || !authService.isOtpProofForPhone(authorization.substring(7), request.getPhoneNumber())) {
            return ResponseEntity.status(401).build();
        }

        if (inviteCode != null && !inviteCode.isBlank()) {
            if (suppliedCode == null || !MessageDigest.isEqual(
                    inviteCode.getBytes(StandardCharsets.UTF_8), suppliedCode.getBytes(StandardCharsets.UTF_8))) {
                return ResponseEntity.status(403)
                        .body(Map.of("message", "Enter the correct invite code to create a vendor profile."));
            }
        }

        try {
            VendorDto vendor = vendorService.registerVendor(request);
            return ResponseEntity.ok(vendor);
        } catch (IllegalArgumentException ex) {
            if ("Phone number already registered".equals(ex.getMessage())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("code", "PHONE_ALREADY_REGISTERED"));
            }
            if ("Slug already taken".equals(ex.getMessage())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("code", "SLUG_TAKEN"));
            }
            throw ex;
        }
    }

    /**
     * Get current vendor profile
     * GET /api/vendor/profile
     */
    @GetMapping("/profile")
    public ResponseEntity<VendorDto> getVendorProfile(HttpServletRequest request) {
        String phoneNumber = authService.extractPhoneNumber(extractToken(request));
        VendorDto vendor = vendorService.getVendorByPhoneNumber(phoneNumber);
        
        if (vendor == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(vendor);
    }

    /**
     * Update vendor profile
     * PUT /api/vendor/profile
     */
    @PutMapping("/profile")
    public ResponseEntity<VendorDto> updateVendorProfile(
            @Valid @RequestBody RegisterVendorRequest request,
            HttpServletRequest httpRequest) {
        String phoneNumber = authService.extractPhoneNumber(extractToken(httpRequest));
        VendorDto currentVendor = vendorService.getVendorByPhoneNumber(phoneNumber);
        
        if (currentVendor == null) {
            return ResponseEntity.notFound().build();
        }
        
        VendorDto updatedVendor = vendorService.updateVendor(currentVendor.getId(), request);
        return ResponseEntity.ok(updatedVendor);
    }

    /**
     * Get public vendor page by slug
     * GET /api/public/vendor/{slug}
     */
    @GetMapping("/public/vendor/{slug}")
    public ResponseEntity<PublicVendorDto> getVendorBySlug(@PathVariable String slug) {
        VendorDto vendor = vendorService.getVendorBySlug(slug);
        
        if (vendor == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(new PublicVendorDto(vendor.getSlug(), vendor.getBusinessName(),
                vendor.getDescription(), vendor.getBannerUrl()));
    }

    /**
     * Extract JWT token from Authorization header
     */
    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
