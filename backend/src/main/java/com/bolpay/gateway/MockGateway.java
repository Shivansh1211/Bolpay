package com.bolpay.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock payment gateway for demo/testing
 * Simulates payment flow without real money
 */
@Component
@Slf4j
public class MockGateway implements PaymentGateway {

    private final Map<String, Boolean> simulatedPayments = new ConcurrentHashMap<>();

    @Override
    public PaymentResult createPayment(String orderCode, BigDecimal amount, String upiId, String customerPhone) {
        String gatewayRef = "MOCK_" + UUID.randomUUID().toString().substring(0, 8);
        
        // Create a mock UPI intent link (in reality, this would open a UPI app)
        String upiIntentLink = String.format(
            "upi://pay?pa=%s&pn=BolPay&am=%s&tr=%s&tn=Payment for %s",
            upiId,
            amount,
            orderCode,
            orderCode
        );

        log.info("MockGateway: Created payment orderCode={}, amount={}, gatewayRef={}", 
                orderCode, amount, gatewayRef);

        return new PaymentResult(gatewayRef, upiIntentLink, true, null);
    }

    @Override
    public boolean verifyWebhookSignature(String signature, String payload) {
        // Mock gateway always accepts signatures for demo
        log.debug("MockGateway: Signature verification (mock - always true)");
        return true;
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
        // For mock, return SUCCESS if we simulated it
        if (gatewayRef.startsWith("MOCK_")) {
            // In a real implementation, we'd check the simulatedPayments map
            // For now, return UNKNOWN as status is managed by the order entity
            return PaymentStatus.UNKNOWN;
        }
        return PaymentStatus.UNKNOWN;
    }

    /**
     * Check if a payment was simulated for an order code
     */
    public boolean wasPaymentSimulated(String orderCode) {
        return simulatedPayments.getOrDefault(orderCode, false);
    }
}
