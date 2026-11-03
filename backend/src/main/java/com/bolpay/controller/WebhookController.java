package com.bolpay.controller;

import com.bolpay.gateway.PaymentGateway;
import com.bolpay.service.WebhookProcessingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {
    private final PaymentGateway paymentGateway;
    private final WebhookProcessingService processingService;
    private final ObjectMapper objectMapper;

    @PostMapping("/payment")
    public ResponseEntity<Map<String, String>> handlePaymentWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @RequestHeader(value = "X-Webhook-Timestamp", required = false) String timestamp) {
        if (signature == null || timestamp == null || signature.isBlank() || timestamp.isBlank()) {
            return ResponseEntity.status(401).body(Map.of("error", "Missing signature or timestamp"));
        }
        long timestampSeconds;
        try { timestampSeconds = Long.parseLong(timestamp); }
        catch (NumberFormatException ex) { return ResponseEntity.status(401).body(Map.of("error", "Invalid timestamp")); }
        if (Math.abs(System.currentTimeMillis() / 1000 - timestampSeconds) > 300) {
            return ResponseEntity.status(401).body(Map.of("error", "Timestamp outside allowed window"));
        }
        if (!paymentGateway.verifyWebhookSignature(signature, timestamp + payload)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid signature"));
        }
        try {
            Map<String, Object> body = objectMapper.readValue(payload, new TypeReference<>() {});
            String eventId = text(body.get("event_id"));
            String orderCode = text(body.get("order_code"));
            String status = text(body.get("status"));
            String gatewayRef = text(body.get("gateway_ref"));
            Object rawAmount = body.get("amount");
            if (eventId == null || eventId.isBlank() || eventId.length() > 100 || orderCode == null || orderCode.isBlank()
                    || status == null || status.isBlank() || rawAmount == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing required event fields"));
            }
            BigDecimal amount = new BigDecimal(rawAmount.toString());
            try {
                processingService.process(eventId, payload, orderCode, amount, status, gatewayRef);
            } catch (DataIntegrityViolationException duplicate) {
                return ResponseEntity.ok(Map.of("message", "Duplicate event already handled"));
            }
            return ResponseEntity.ok(Map.of("message", "Webhook accepted"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            log.warn("Invalid webhook payload", ex);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid webhook payload"));
        }
    }

    private String text(Object value) { return value == null ? null : value.toString(); }
}
