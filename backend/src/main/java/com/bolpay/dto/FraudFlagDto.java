package com.bolpay.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record FraudFlagDto(
        String orderCode,
        String token,
        BigDecimal amount,
        BigDecimal score,
        List<String> reasons,
        String modelVersion,
        String scorer,
        LocalDateTime createdAt) {}
