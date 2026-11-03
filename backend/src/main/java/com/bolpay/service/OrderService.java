package com.bolpay.service;

import com.bolpay.dto.CreateOrderRequest;
import com.bolpay.dto.OrderDto;
import com.bolpay.entity.Order;
import com.bolpay.entity.Vendor;
import com.bolpay.gateway.PaymentGateway;
import com.bolpay.repository.OrderRepository;
import com.bolpay.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Service for order operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final VendorRepository vendorRepository;
    private final PaymentGateway paymentGateway;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${order.slow-after:60}")
    private int slowAfterSeconds;

    @Value("${order.expire-after:150}")
    private int expireAfterSeconds;

    @Value("${order.escalate-after:240}")
    private int escalateAfterSeconds;

    private static final int ORDER_CODE_LENGTH = 8;
    private static final String TOKEN_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String TOKEN_DIGITS = "23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Create a new payment order
     */
    @Transactional
    public OrderDto createOrder(CreateOrderRequest request, String deviceFingerprint) {
        // Get vendor by slug
        Vendor vendor = vendorRepository.findBySlugForUpdate(request.getVendorSlug())
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found"));

        if (!vendor.getIsActive()) {
            throw new IllegalArgumentException("Vendor is not active");
        }

        // Generate unique order code
        String orderCode = generateOrderCode();
        while (orderRepository.findByOrderCode(orderCode).isPresent()) {
            orderCode = generateOrderCode();
        }

        // Generate token (1 letter + 2-3 digits, unique per vendor among ACTIVE orders)
        String token = allocateActiveToken(vendor.getId());

        // Hash device fingerprint and payer info
        String deviceFingerprintHash = hashString(deviceFingerprint);
        String payerHash = request.getCustomerPhone() != null 
                ? hashString(request.getCustomerPhone()) 
                : null;

        // Insert the row before calling the gateway so the database checks the active token
        // uniqueness constraint before an external payment session is created.
        LocalDateTime now = LocalDateTime.now();
        Order order = Order.builder()
                .vendor(vendor)
                .vendorId(vendor.getId())
                .status(Order.OrderStatus.PENDING)
                .amount(request.getAmount())
                .orderCode(orderCode)
                .token(token)
                .createdAt(now)
                .expiresAt(now.plusSeconds(expireAfterSeconds))
                .escalateAt(now.plusSeconds(escalateAfterSeconds))
                .deviceFingerprintHash(deviceFingerprintHash)
                .payerHash(payerHash)
                .build();

        order = orderRepository.saveAndFlush(order);

        PaymentGateway.PaymentResult paymentResult = paymentGateway.createPayment(
                orderCode, request.getAmount(), vendor.getUpiId(), request.getCustomerPhone());
        if (!paymentResult.success()) {
            throw new RuntimeException("Failed to create payment: " + paymentResult.errorMessage());
        }
        order.setPaymentSessionId(paymentResult.paymentSessionId());
        order.setGatewayRef(paymentResult.gatewayRef());
        order = orderRepository.save(order);

        log.info("Created order: code={}, amount={}, vendor={}", orderCode, request.getAmount(), vendor.getBusinessName());

        return mapToDto(order);
    }

    /**
     * Get order by code
     */
    @Transactional(readOnly = true)
    public OrderDto getOrderByCode(String orderCode) {
        return orderRepository.findByOrderCode(orderCode)
                .map(this::mapToDto)
                .orElse(null);
    }

    /**
     * Get orders for a vendor
     */
    public List<OrderDto> getOrdersByVendor(Long vendorId) {
        return orderRepository.findByVendorIdOrderByCreatedAtDesc(vendorId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    /**
     * Get order by token and vendor (for quick check)
     */
    public OrderDto getOrderByTokenAndVendor(String token, Long vendorId) {
        return orderRepository.findByVendorIdAndTokenAndStatus(vendorId, token, Order.OrderStatus.PENDING)
                .map(this::mapToDto)
                .orElse(null);
    }

    /**
     * Mark order as paid (called by webhook)
     */
    @Transactional
    public void markOrderAsPaid(String orderCode, String gatewayRef) {
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        // Check if already paid
        if (order.getStatus() == Order.OrderStatus.PAID || order.getStatus() == Order.OrderStatus.LATE_PAID) {
            log.info("Order {} already paid, skipping", orderCode);
            return;
        }

        // Check if expired - mark as LATE_PAID instead of FLAGGED
        if (order.getStatus() == Order.OrderStatus.EXPIRED) {
            log.warn("Order {} is expired, marking as LATE_PAID", orderCode);
            order.setStatus(Order.OrderStatus.LATE_PAID);
            order.setPaidAt(LocalDateTime.now());
            orderRepository.save(order);
            
            // Send WebSocket notification for late payment (vendor should review)
            messagingTemplate.convertAndSend(
                    "/topic/vendor/" + order.getVendorId(),
                    mapToDto(order)
            );
            return;
        }

        // Mark as paid
        order.setStatus(Order.OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        orderRepository.save(order);

        log.info("Marked order {} as PAID", orderCode);

        // Send WebSocket notification to vendor
        messagingTemplate.convertAndSend(
                "/topic/vendor/" + order.getVendorId(),
                mapToDto(order)
        );
    }

    /**
     * Mark expired orders
     */
    @Transactional
    public void markExpiredOrders() {
        List<Order> expiredOrders = orderRepository.findByStatusAndExpiresAtBefore(
                Order.OrderStatus.PENDING,
                LocalDateTime.now()
        );

        for (Order order : expiredOrders) {
            order.setStatus(Order.OrderStatus.EXPIRED);
            orderRepository.save(order);
            log.info("Marked order {} as EXPIRED", order.getOrderCode());
        }
    }

    /**
     * Get statistics for ML fraud detection
     */
    public long getExpiredOrdersCount(String deviceFingerprintHash, LocalDateTime since) {
        return orderRepository.findByDeviceFingerprintHashAndCreatedAtAfter(deviceFingerprintHash, since)
                .stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.EXPIRED)
                .count();
    }

    public long getPendingOrdersCount(Long vendorId, LocalDateTime since) {
        return orderRepository.findByVendorIdAndCreatedAtAfter(vendorId, since)
                .stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.PENDING)
                .count();
    }

    /**
     * Generate random order code
     */
    private String generateOrderCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < ORDER_CODE_LENGTH; i++) {
            code.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return code.toString();
    }

    /**
     * Generate token (1 letter + 2-3 digits, skip confusing chars like O/0 and I/1)
     * Unique per vendor among ACTIVE orders
     */
    String allocateActiveToken(Long vendorId) {
        String token = findAvailableToken(vendorId, 2);
        if (token != null) return token;
        token = findAvailableToken(vendorId, 3);
        if (token != null) return token;
        throw new IllegalStateException("Active order token capacity exhausted for vendor");
    }

    private String findAvailableToken(Long vendorId, int digitsCount) {
        List<String> candidates = new ArrayList<>();
        int combinations = (int) Math.pow(TOKEN_DIGITS.length(), digitsCount);
        for (char letter : TOKEN_LETTERS.toCharArray()) {
            for (int value = 0; value < combinations; value++) {
                int remaining = value;
                StringBuilder candidate = new StringBuilder().append(letter);
                for (int position = 0; position < digitsCount; position++) {
                    candidate.append(TOKEN_DIGITS.charAt(remaining % TOKEN_DIGITS.length()));
                    remaining /= TOKEN_DIGITS.length();
                }
                candidates.add(candidate.toString());
            }
        }
        Collections.shuffle(candidates, RANDOM);
        for (String candidate : candidates) {
            if (orderRepository.findByVendorIdAndTokenAndStatus(
                    vendorId, candidate, Order.OrderStatus.PENDING).isEmpty()) return candidate;
        }
        return null;
    }

    /**
     * Hash a string using SHA-256
     */
    private String hashString(String input) {
        if (input == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Map Order entity to DTO
     */
    private OrderDto mapToDto(Order order) {
        return OrderDto.builder()
                .id(order.getId())
                .vendorId(order.getVendorId())
                .vendorBusinessName(order.getVendor() != null ? order.getVendor().getBusinessName() : null)
                .status(order.getStatus().name())
                .amount(order.getAmount())
                .orderCode(order.getOrderCode())
                .token(order.getToken())
                .paymentSessionId(order.getPaymentSessionId())
                .gatewayRef(order.getGatewayRef())
                .createdAt(order.getCreatedAt())
                .expiresAt(order.getExpiresAt())
                .escalateAt(order.getEscalateAt())
                .paidAt(order.getPaidAt())
                .build();
    }
}
