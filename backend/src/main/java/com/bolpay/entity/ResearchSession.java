package com.bolpay.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Research session for collecting data in research mode
 */
@Entity
@Table(name = "research_session")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(name = "vendor_id", insertable = false, updatable = false)
    private Long vendorId;

    @Column(name = "label", nullable = false, length = 50)
    private String label; // "honest" or "simulated_fake"

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "total_orders")
    private Integer totalOrders;

    @Column(name = "paid_orders")
    private Integer paidOrders;

    @Column(name = "expired_orders")
    private Integer expiredOrders;

    @Column(name = "flagged_orders")
    private Integer flaggedOrders;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
