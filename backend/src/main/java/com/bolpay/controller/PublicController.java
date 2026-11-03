package com.bolpay.controller;

import com.bolpay.entity.Vendor;
import com.bolpay.dto.PublicVendorDto;
import com.bolpay.repository.VendorRepository;
import com.bolpay.service.ScanSessionService;
import lombok.RequiredArgsConstructor;
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
public class PublicController {

    private final VendorRepository vendorRepository;
    private final ScanSessionService scanSessionService;

    /**
     * Track scan session for a vendor
     * POST /api/public/scan/{slug}
     */
    @PostMapping("/scan/{slug}")
    public ResponseEntity<Map<String, String>> trackScan(
            @PathVariable String slug,
            @RequestBody Map<String, String> request) {
        String deviceId = request.get("deviceId");
        try {
            scanSessionService.recordScan(slug, deviceId);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("status", "rejected"));
        }
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
