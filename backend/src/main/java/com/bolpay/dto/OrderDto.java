package com.bolpay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for Order entity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDto {

    private Long id;
    private Long vendorId;
    private String vendorBusinessName;
    private String status;
    private BigDecimal amount;
    private String orderCode;
    private String token;
    private String paymentSessionId;
    private String gatewayRef;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime escalateAt;
    private LocalDateTime paidAt;
    private String upiIntentLink;
}
