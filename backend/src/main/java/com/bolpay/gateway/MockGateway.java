package com.bolpay.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Mock payment gateway for demo/testing
 * Simulates payment flow without real money
 */
@Component
@ConditionalOnProperty(name = "payment.gateway", havingValue = "mock", matchIfMissing = true)
@Slf4j
public class MockGateway implements PaymentGateway {

    @Value("${cashfree.internal-url}") private String gatewayUrl;
    @Value("${cashfree.client-id}") private String clientId;
    @Value("${cashfree.client-secret}") private String clientSecret;
    @Value("${cashfree.webhook-secret}") private String webhookSecret;
    private final RestTemplate restTemplate = new RestTemplate();

    private final Map<String, Boolean> simulatedPayments = new ConcurrentHashMap<>();

    @Override
    public PaymentResult createPayment(String orderCode, BigDecimal amount, String upiId, String customerPhone) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-client-id", clientId);
            headers.set("x-client-secret", clientSecret);
            headers.set("x-api-version", "2025-01-01");
            Map<String, Object> body = Map.of("order_id", orderCode, "order_amount", amount,
                    "order_currency", "INR", "order_meta", Map.of("return_url", ""));
            ResponseEntity<Map> response = restTemplate.postForEntity(gatewayUrl + "/pg/orders",
                    new HttpEntity<>(body, headers), Map.class);
            Map result = response.getBody();
            if (result == null || result.get("payment_session_id") == null)
                return new PaymentResult(null, null, false, "Mock gateway returned no payment session");
            return new PaymentResult((String) result.get("cf_order_id"),
                    (String) result.get("payment_session_id"), true, null);
        } catch (Exception e) {
            log.error("Mock gateway order creation failed", e);
            return new PaymentResult(null, null, false, "Mock gateway unavailable");
        }
    }

    @Override
    public boolean verifyWebhookSignature(String signature, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return signature != null && java.security.MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            log.error("Mock webhook signature verification failed", e);
            return false;
        }
    }

    @Override
    public WebhookData parseWebhook(String payload) {
        // For mock, we expect a simple JSON payload
        // This would be called from the simulate-payment endpoint
        log.info("MockGateway: Parsing webhook payload");
        
        // In a real scenario, this would parse the gateway's webhook format
        // For mock, we'll return null and handle simulation separately
        return null;
    }

    @Override
    public boolean simulatePayment(String orderCode) {
        log.info("MockGateway: Simulating payment for orderCode={}", orderCode);
        simulatedPayments.put(orderCode, true);
        return true;
    }

    @Override
    public PaymentStatus getPaymentStatus(String gatewayRef) {
        try {
            String orderCode = gatewayRef != null && gatewayRef.startsWith("CF_")
                    ? gatewayRef.substring(3) : gatewayRef;
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-client-id", clientId);
            headers.set("x-client-secret", clientSecret);
            headers.set("x-api-version", "2025-01-01");
            Map response = restTemplate.exchange(gatewayUrl + "/pg/orders/" + orderCode,
                    HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody();
            if (response == null) return PaymentStatus.UNKNOWN;
            return switch (String.valueOf(response.get("order_status"))) {
                case "PAID" -> PaymentStatus.SUCCESS;
                case "ACTIVE", "PENDING" -> PaymentStatus.PENDING;
                case "FAILED", "EXPIRED" -> PaymentStatus.FAILED;
                default -> PaymentStatus.UNKNOWN;
            };
        } catch (Exception e) {
            log.warn("Mock gateway status lookup failed");
            return PaymentStatus.UNKNOWN;
        }
    }

    /**
     * Check if a payment was simulated for an order code
     */
    public boolean wasPaymentSimulated(String orderCode) {
        return simulatedPayments.getOrDefault(orderCode, false);
    }
}
