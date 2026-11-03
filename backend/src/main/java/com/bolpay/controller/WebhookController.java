package com.bolpay.controller;

import com.bolpay.entity.Order;
import com.bolpay.entity.WebhookEvent;
import com.bolpay.gateway.PaymentGateway;
import com.bolpay.repository.OrderRepository;
import com.bolpay.repository.WebhookEventRepository;
import com.bolpay.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Controller for payment gateway webhooks
 */
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final PaymentGateway paymentGateway;
    private final OrderRepository orderRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    /**
     * Handle payment gateway webhook
     * POST /api/webhook/payment
     * Signature and timestamp headers are REQUIRED
     */
    @PostMapping("/payment")
    public ResponseEntity<Map<String, String>> handlePaymentWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Webhook-Signature", required = true) String signature,
            @RequestHeader(value = "X-Webhook-Timestamp", required = true) String timestamp,
            @RequestHeader(value = "X-Webhook-Version", required = false) String version,
            HttpServletRequest request) {

        log.info("Received payment webhook");

        // Verify signature is present
        if (signature == null || signature.isEmpty()) {
            log.warn("Missing webhook signature");
            return ResponseEntity.status(401).body(Map.of("error", "Missing signature"));
        }

        // Verify timestamp is present and not too old (5 minutes)
        if (timestamp == null || timestamp.isEmpty()) {
            log.warn("Missing webhook timestamp");
            return ResponseEntity.status(401).body(Map.of("error", "Missing timestamp"));
        }

        try {
            long webhookTimestamp = Long.parseLong(timestamp);
            long currentTimestamp = System.currentTimeMillis() / 1000;
            long timestampDiff = Math.abs(currentTimestamp - webhookTimestamp);

            if (timestampDiff > 300) { // 5 minutes
                log.warn("Webhook timestamp too old: {} seconds", timestampDiff);
                return ResponseEntity.status(401).body(Map.of("error", "Timestamp too old"));
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid webhook timestamp format");
            return ResponseEntity.status(401).body(Map.of("error", "Invalid timestamp format"));
        }

        // Verify signature (timestamp + rawBody)
        String signaturePayload = timestamp + payload;
        if (!paymentGateway.verifyWebhookSignature(signature, signaturePayload)) {
            log.warn("Invalid webhook signature");
            return ResponseEntity.status(401).body(Map.of("error", "Invalid signature"));
        }

        // Parse JSON payload
        Map<String, Object> payloadMap;
        try {
            payloadMap = objectMapper.readValue(payload, Map.class);
        } catch (Exception e) {
            log.error("Failed to parse webhook payload", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid JSON payload"));
        }

        // Extract event ID for idempotency
        String eventId = (String) payloadMap.get("event_id");
        if (eventId == null) {
            eventId = "evt_" + System.currentTimeMillis();
        }

        // Check if already processed (idempotency)
        if (webhookEventRepository.existsByEventId(eventId)) {
            log.info("Webhook event {} already processed, skipping", eventId);
            return ResponseEntity.ok(Map.of("message", "Already processed"));
        }

        // Parse webhook data
        String orderCode = (String) payloadMap.get("order_code");
        BigDecimal amount = payloadMap.get("amount") != null
                ? new BigDecimal(payloadMap.get("amount").toString())
                : null;
        String status = (String) payloadMap.get("status");
        String gatewayRef = (String) payloadMap.get("gateway_ref");

        if (orderCode == null) {
            log.error("Webhook missing order_code");
            return ResponseEntity.badRequest().body(Map.of("error", "Missing order_code"));
        }

        // Find order
        Order order = orderRepository.findByOrderCode(orderCode).orElse(null);
        if (order == null) {
            log.error("Order not found for order_code: {}", orderCode);
            return ResponseEntity.badRequest().body(Map.of("error", "Order not found"));
        }

        // Validate amount
        if (amount != null && order.getAmount().compareTo(amount) != 0) {
            log.error("Amount mismatch for order {}: expected {}, got {}", 
                    orderCode, order.getAmount(), amount);
            
            // Record as flagged
            recordWebhookEvent(eventId, order, payload.toString(), WebhookEvent.WebhookStatus.FLAGGED);
            return ResponseEntity.badRequest().body(Map.of("error", "Amount mismatch"));
        }

        // Check if order is expired
        if (order.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Payment received after expiry for order {}", orderCode);
            
            // Record as flagged
            recordWebhookEvent(eventId, order, payload, WebhookEvent.WebhookStatus.FLAGGED);
            orderService.markOrderAsPaid(orderCode, gatewayRef); // Will mark as FLAGGED
            return ResponseEntity.ok(Map.of("message", "Payment after expiry - flagged"));
        }

        // Process payment
        if ("success".equalsIgnoreCase(status) || "paid".equalsIgnoreCase(status)) {
            orderService.markOrderAsPaid(orderCode, gatewayRef);
            recordWebhookEvent(eventId, order, payload, WebhookEvent.WebhookStatus.PROCESSED);
            return ResponseEntity.ok(Map.of("message", "Payment processed"));
        } else {
            log.warn("Non-success status for order {}: {}", orderCode, status);
            recordWebhookEvent(eventId, order, payload, WebhookEvent.WebhookStatus.REJECTED);
            return ResponseEntity.ok(Map.of("message", "Payment not successful"));
        }
    }

    private void recordWebhookEvent(String eventId, Order order, String payload, WebhookEvent.WebhookStatus status) {
        WebhookEvent webhookEvent = WebhookEvent.builder()
                .eventId(eventId)
                .order(order)
                .orderId(order.getId())
                .gateway("mock")
                .payload(payload)
                .status(status)
                .build();
        webhookEventRepository.save(webhookEvent);
    }
}
