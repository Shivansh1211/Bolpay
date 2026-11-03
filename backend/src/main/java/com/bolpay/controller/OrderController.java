package com.bolpay.controller;

import com.bolpay.dto.CreateOrderRequest;
import com.bolpay.dto.OrderDto;
import com.bolpay.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for order endpoints
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;
    private final AuthService authService;

    /**
     * Create a new payment order
     * POST /api/orders/create
     */
    @PostMapping("/create")
    public ResponseEntity<OrderDto> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            HttpServletRequest httpRequest) {
        
        // Extract device fingerprint from request (simplified - in production, use proper fingerprinting)
        String deviceFingerprint = httpRequest.getHeader("User-Agent") + httpRequest.getRemoteAddr();
        
        OrderDto order = orderService.createOrder(request, deviceFingerprint);
        return ResponseEntity.ok(order);
    }

    /**
     * Get order by code
     * GET /api/orders/{orderCode}
     */
    @GetMapping("/{orderCode}")
    public ResponseEntity<OrderDto> getOrderByCode(@PathVariable String orderCode) {
        OrderDto order = orderService.getOrderByCode(orderCode);
        
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(order);
    }

    /**
     * Get orders for current vendor
     * GET /api/orders/vendor
     * Vendor ID is extracted from JWT, not from request parameter (prevents IDOR)
     */
    @GetMapping("/vendor")
    public ResponseEntity<List<OrderDto>> getVendorOrders(
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.Authentication authentication) {
        Long vendorId = authService.extractVendorIdFromAuthentication(authentication);
        List<OrderDto> orders = orderService.getOrdersByVendor(vendorId);
        return ResponseEntity.ok(orders);
    }

    /**
     * Check order status (public endpoint for customer verification)
     * GET /api/orders/{orderCode}/status
     */
    @GetMapping("/{orderCode}/status")
    public ResponseEntity<Map<String, Object>> getOrderStatus(@PathVariable String orderCode) {
        OrderDto order = orderService.getOrderByCode(orderCode);

        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("orderCode", order.getOrderCode());
        response.put("status", order.getStatus());
        response.put("amount", order.getAmount());
        response.put("paidAt", order.getPaidAt());

        return ResponseEntity.ok(response);
    }

    /**
     * Quick check for vendor to verify order status
     * GET /api/orders/quick-check/{orderCode}
     */
    @GetMapping("/quick-check/{orderCode}")
    public ResponseEntity<Map<String, Object>> quickCheckOrder(@PathVariable String orderCode) {
        OrderDto order = orderService.getOrderByCode(orderCode);
        
        if (order == null) {
            return ResponseEntity.ok(Map.of(
                    "found", false,
                    "message", "Order not found"
            ));
        }
        
        return ResponseEntity.ok(Map.of(
                "found", true,
                "orderCode", order.getOrderCode(),
                "status", order.getStatus(),
                "amount", order.getAmount(),
                "paidAt", order.getPaidAt(),
                "createdAt", order.getCreatedAt()
        ));
    }
}
