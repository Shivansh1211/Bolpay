package com.bolpay.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Order entity representing a payment order
 */
@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(name = "vendor_id", insertable = false, updatable = false)
    private Long vendorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "order_code", nullable = false, unique = true, length = 20)
    private String orderCode;

    @Column(name = "token", nullable = false, length = 5)
    private String token; // 1 letter + 2-3 digits (e.g., "K72")

    @Column(name = "payment_session_id", length = 100)
    private String paymentSessionId;

    @Column(name = "gateway_ref", length = 100)
    private String gatewayRef;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "escalate_at")
    private LocalDateTime escalateAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "device_fingerprint_hash", length = 64)
    private String deviceFingerprintHash;

    @Column(name = "payer_hash", length = 64)
    private String payerHash;

    @Column(name = "upi_intent_link", columnDefinition = "TEXT")
    private String upiIntentLink;

    public enum OrderStatus {
        PENDING,
        PAID,
        EXPIRED,
        FLAGGED,
        LATE_PAID // Payment received after expiry
    }
}
