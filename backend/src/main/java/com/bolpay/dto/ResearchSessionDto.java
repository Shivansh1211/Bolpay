package com.bolpay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchSessionDto {
    private Long id;
    private Long vendorId;
    private String label;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer totalOrders;
    private Integer paidOrders;
    private Integer expiredOrders;
    private Integer flaggedOrders;
}
