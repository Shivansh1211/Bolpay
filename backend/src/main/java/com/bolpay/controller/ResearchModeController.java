package com.bolpay.controller;

import com.bolpay.dto.ResearchSessionDto;
import com.bolpay.service.AuthService;
import com.bolpay.service.ResearchModeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for research mode endpoints
 */
@RestController
@RequestMapping("/api/vendor/research")
@RequiredArgsConstructor
@Slf4j
public class ResearchModeController {

    private final ResearchModeService researchModeService;
    private final AuthService authService;

    /**
     * Create a research session
     * POST /api/vendor/research/sessions
     */
    @PostMapping("/sessions")
    public ResponseEntity<ResearchSessionDto> createSession(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        Long vendorId = authService.extractVendorIdFromAuthentication(authentication);
        String label = request.get("label"); // "honest" or "simulated_fake"
        
        ResearchSessionDto session = researchModeService.createSession(vendorId, label);
        return ResponseEntity.ok(session);
    }

    /**
     * Get research report
     * GET /api/vendor/research/report
     */
    @GetMapping("/report")
    public ResponseEntity<Map<String, Object>> getReport(Authentication authentication) {
        Long vendorId = authService.extractVendorIdFromAuthentication(authentication);
        Map<String, Object> report = researchModeService.getReport(vendorId);
        return ResponseEntity.ok(report);
    }

    /**
     * Export research data as CSV
     * GET /api/vendor/research/export
     */
    @GetMapping("/export")
    public ResponseEntity<String> exportData(Authentication authentication) {
        Long vendorId = authService.extractVendorIdFromAuthentication(authentication);
        String csv = researchModeService.exportToCsv(vendorId);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=research_data.csv")
                .body(csv);
    }
}
