package com.bolpay.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Incident log for unresolved expired orders, late payments, and flags
 */
@Entity
@Table(name = "incident_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "order_id", insertable = false, updatable = false)
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(name = "vendor_id", insertable = false, updatable = false)
    private Long vendorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", nullable = false, length = 50)
    private IncidentType incidentType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "resolved", nullable = false)
    private Boolean resolved = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public enum IncidentType {
        TIMEOUT,
        LATE_PAYMENT,
        PATTERN_FLAG,
        HIGH_VALUE_PENDING,
        BULK_ORDER,
        RECONCILIATION_FAILURE
    }
}
