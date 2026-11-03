package com.bolpay.controller;

import com.bolpay.dto.RegisterVendorRequest;
import com.bolpay.dto.VendorDto;
import com.bolpay.service.AuthService;
import com.bolpay.service.VendorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    /**
     * Register a new vendor
     * POST /api/vendor/register
     */
    @PostMapping("/register")
    public ResponseEntity<VendorDto> registerVendor(@Valid @RequestBody RegisterVendorRequest request) {
        VendorDto vendor = vendorService.registerVendor(request);
        return ResponseEntity.ok(vendor);
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
    public ResponseEntity<VendorDto> getVendorBySlug(@PathVariable String slug) {
        VendorDto vendor = vendorService.getVendorBySlug(slug);
        
        if (vendor == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(vendor);
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
