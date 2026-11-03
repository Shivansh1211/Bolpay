package com.bolpay.controller;

import com.bolpay.dto.OrderDto;
import com.bolpay.entity.IncidentLog;
import com.bolpay.service.AuthService;
import com.bolpay.service.IncidentLogService;
import com.bolpay.service.OrderService;
import com.bolpay.service.ScanSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for vendor board endpoints
 */
@RestController
@RequestMapping("/api/vendor")
@RequiredArgsConstructor
@Slf4j
public class VendorBoardController {

    private final OrderService orderService;
    private final IncidentLogService incidentLogService;
    private final AuthService authService;
    private final ScanSessionService scanSessionService;

    /**
     * Get vendor board data
     * GET /api/vendor/board
     */
    @GetMapping("/board")
    public ResponseEntity<Map<String, Object>> getVendorBoard(Authentication authentication) {
        Long vendorId = authService.extractVendorIdFromAuthentication(authentication);
        
        // Get paying now (PENDING orders)
        List<OrderDto> pendingOrders = orderService.getOrdersByVendor(vendorId).stream()
                .filter(o -> "PENDING".equals(o.getStatus()))
                .toList();
        
        // Get paid in last 10 minutes
        LocalDateTime tenMinutesAgo = LocalDateTime.now().minusMinutes(10);
        List<OrderDto> recentPaidOrders = orderService.getOrdersByVendor(vendorId).stream()
                .filter(o -> "PAID".equals(o.getStatus()) && o.getPaidAt() != null && o.getPaidAt().isAfter(tenMinutesAgo))
                .toList();
        
        // Get all orders
        List<OrderDto> allOrders = orderService.getOrdersByVendor(vendorId);
        
        // Get unresolved incidents
        List<IncidentLog> unresolvedIncidents = incidentLogService.getUnresolvedIncidents(vendorId);
        
        int scannedCount = Math.toIntExact(scanSessionService.countRecentScans(
                vendorId, LocalDateTime.now().minusMinutes(10)));
        
        Map<String, Object> response = new HashMap<>();
        response.put("scanned", scannedCount);
        response.put("payingNow", pendingOrders.size());
        response.put("paidLast10Min", recentPaidOrders.size());
        response.put("paidOrders", recentPaidOrders);
        response.put("waitingOrders", pendingOrders);
        response.put("allOrders", allOrders);
        response.put("unresolvedIncidents", unresolvedIncidents);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Quick check by token
     * GET /api/vendor/orders/check/{token}
     */
    @GetMapping("/orders/check/{token}")
    public ResponseEntity<Map<String, Object>> checkOrderByToken(
            @PathVariable String token,
            Authentication authentication) {
        Long vendorId = authService.extractVendorIdFromAuthentication(authentication);
        
        OrderDto order = orderService.getOrderByTokenAndVendor(token, vendorId);
        
        if (order == null) {
            return ResponseEntity.ok(Map.of(
                    "found", false,
                    "message", "Order not found"
            ));
        }
        
        return ResponseEntity.ok(Map.of(
                "found", true,
                "token", order.getToken(),
                "status", order.getStatus(),
                "amount", order.getAmount(),
                "paidAt", order.getPaidAt()
        ));
    }

    /**
     * Get incidents for vendor
     * GET /api/vendor/incidents
     */
    @GetMapping("/incidents")
    public ResponseEntity<List<IncidentLog>> getIncidents(Authentication authentication) {
        Long vendorId = authService.extractVendorIdFromAuthentication(authentication);
        List<IncidentLog> incidents = incidentLogService.getIncidentsByVendor(vendorId);
        return ResponseEntity.ok(incidents);
    }

    /**
     * Get QR card data
     * GET /api/vendor/qr-card
     */
    @GetMapping("/qr-card")
    public ResponseEntity<Map<String, Object>> getQrCard(Authentication authentication) {
        Long vendorId = authService.extractVendorIdFromAuthentication(authentication);
        // This would return QR card data for printing
        // For now, return placeholder
        return ResponseEntity.ok(Map.of(
                "message", "QR card generation not yet implemented"
        ));
    }
}
