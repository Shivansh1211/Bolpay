package com.bolpay.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Cashfree payment gateway implementation
 * Implements Cashfree's real API contract with configurable base URL
 * Switch between mock and real sandbox via CASHFREE_BASE_URL environment variable
 */
@Component
@Slf4j
public class CashfreeGateway implements PaymentGateway {

    @Value("${cashfree.base-url}")
    private String baseUrl;

    @Value("${cashfree.public-url}")
    private String publicUrl;

    @Value("${cashfree.client-id}")
    private String clientId;

    @Value("${cashfree.client-secret}")
    private String clientSecret;

    @Value("${cashfree.api-version}")
    private String apiVersion;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public PaymentResult createPayment(String orderCode, BigDecimal amount, String upiId, String customerPhone) {
        try {
            String url = baseUrl + "/orders";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-client-id", clientId);
            headers.set("x-client-secret", clientSecret);
            headers.set("x-api-version", apiVersion);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("order_id", orderCode);
            requestBody.put("order_amount", amount);
            requestBody.put("order_currency", "INR");
            if (customerPhone != null) {
                requestBody.put("customer_details", Map.of(
                    "customer_id", customerPhone,
                    "customer_phone", customerPhone
                ));
            }
            requestBody.put("order_meta", Map.of(
                "return_url", publicBaseUrl + "/o/" + orderCode,
                "payment_methods", "upi"
            ));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // Make API call to Cashfree (mock or real)
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

            if (response != null && response.containsKey("payment_session_id")) {
                String paymentSessionId = (String) response.get("payment_session_id");
                String cfOrderId = (String) response.get("cf_order_id");
                String orderExpiryTime = (String) response.get("order_expiry_time");

                log.info("CashfreeGateway: Created payment orderCode={}, amount={}, paymentSessionId={}", 
                    orderCode, amount, paymentSessionId);
                
                // Return result with payment_session_id instead of UPI intent link
                return new PaymentResult(cfOrderId, paymentSessionId, true, null);
            } else {
                log.error("CashfreeGateway: Invalid response from gateway");
                return new PaymentResult(null, null, false, "Invalid gateway response");
            }

        } catch (Exception e) {
            log.error("CashfreeGateway: Failed to create payment", e);
            return new PaymentResult(null, null, false, e.getMessage());
        }
    }

    @Override
    public boolean verifyWebhookSignature(String signature, String payload) {
        try {
            // Cashfree signature: Base64(HMAC_SHA256(secret, timestamp + rawBody))
            // The payload already includes timestamp from the caller
            String expectedSignature = generateSignature(payload);
            
            // Constant-time comparison to prevent timing attacks
            return constantTimeEquals(signature, expectedSignature);
        } catch (Exception e) {
            log.error("CashfreeGateway: Signature verification failed", e);
            return false;
        }
    }

    @Override
    public WebhookData parseWebhook(String payload) {
        // Parse Cashfree webhook format
        // This would use a JSON parser in production
        log.info("CashfreeGateway: Parsing webhook");
        return null; // Implementation would parse actual Cashfree webhook
    }

    @Override
    public boolean simulatePayment(String orderCode) {
        // Cashfree doesn't support simulation - this is for MockGateway only
        log.warn("CashfreeGateway: simulatePayment not supported for Cashfree");
        return false;
    }

    @Override
    public PaymentStatus getPaymentStatus(String gatewayRef) {
        try {
            String url = baseUrl + "/orders/" + gatewayRef;

            HttpHeaders headers = new HttpHeaders();
            headers.set("x-client-id", clientId);
            headers.set("x-client-secret", clientSecret);
            headers.set("x-api-version", apiVersion);

            HttpEntity<Void> request = new HttpEntity<>(headers);
            Map<String, Object> response = restTemplate.getForObject(url, request, Map.class);
            
            if (response != null) {
                String orderStatus = (String) response.get("order_status");
                if ("PAID".equals(orderStatus)) {
                    return PaymentStatus.SUCCESS;
                } else if ("ACTIVE".equals(orderStatus) || "PENDING".equals(orderStatus)) {
                    return PaymentStatus.PENDING;
                } else if ("FAILED".equals(orderStatus) || "EXPIRED".equals(orderStatus)) {
                    return PaymentStatus.FAILED;
                }
            }
            
            return PaymentStatus.UNKNOWN;
        } catch (Exception e) {
            log.error("CashfreeGateway: Failed to get payment status", e);
            return PaymentStatus.UNKNOWN;
        }
    }

    private String generateSignature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate signature", e);
        }
    }

    /**
     * Constant-time comparison to prevent timing attacks
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        
        if (a.length() != b.length()) {
            return false;
        }
        
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
