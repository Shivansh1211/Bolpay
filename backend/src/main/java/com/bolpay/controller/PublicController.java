package com.bolpay.controller;

import com.bolpay.entity.Vendor;
import com.bolpay.dto.PublicVendorDto;
import com.bolpay.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Public controller for customer-facing endpoints
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Slf4j
public class PublicController {

    private final VendorRepository vendorRepository;

    /**
     * Track scan session for a vendor
     * POST /api/public/scan/{slug}
     */
    @PostMapping("/scan/{slug}")
    public ResponseEntity<Map<String, String>> trackScan(
            @PathVariable String slug,
            @RequestBody Map<String, String> request) {
        String deviceId = request.get("deviceId");
        log.info("Scan tracked for vendor: {}, device: {}", slug, deviceId);
        
        // In a real implementation, this would increment a scan counter
        // For now, just acknowledge
        return ResponseEntity.ok(Map.of("status", "tracked"));
    }

    /**
     * Get vendor by ID (for QR page)
     * GET /api/vendors/{id}
     */
    @GetMapping("/vendors/{id}")
    public ResponseEntity<PublicVendorDto> getVendorById(@PathVariable Long id) {
        return vendorRepository.findById(id)
                .map(v -> ResponseEntity.ok(new PublicVendorDto(v.getSlug(), v.getBusinessName(), v.getDescription(), v.getBannerUrl())))
                .orElse(ResponseEntity.notFound().build());
    }
}
