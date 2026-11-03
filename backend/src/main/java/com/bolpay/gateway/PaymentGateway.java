package com.bolpay.gateway;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Interface for payment gateway implementations
 */
public interface PaymentGateway {

    /**
     * Create a payment order and return UPI intent link
     */
    PaymentResult createPayment(String orderCode, BigDecimal amount, String upiId, String customerPhone);

    /**
     * Verify webhook signature
     */
    boolean verifyWebhookSignature(String signature, String payload);

    /**
     * Parse webhook payload and extract payment details
     */
    WebhookData parseWebhook(String payload);

    /**
     * Simulate payment (for demo/testing with MockGateway)
     */
    boolean simulatePayment(String orderCode);

    /**
     * Get payment status from gateway
     */
    PaymentStatus getPaymentStatus(String gatewayRef);

    /**
     * Result of payment creation
     */
    record PaymentResult(
        String gatewayRef,
        String upiIntentLink,
        boolean success,
        String errorMessage
    ) {}

    /**
     * Parsed webhook data
     */
    record WebhookData(
        String event_id,
        String orderCode,
        BigDecimal amount,
        String status,
        String gatewayRef,
        Map<String, Object> rawData
    ) {}

    /**
     * Payment status from gateway
     */
    enum PaymentStatus {
        SUCCESS,
        PENDING,
        FAILED,
        UNKNOWN
    }
}
