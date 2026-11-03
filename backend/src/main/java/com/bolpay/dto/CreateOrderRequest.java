package com.bolpay.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for creating a new order
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotBlank(message = "Vendor slug is required")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Vendor slug must contain only lowercase letters, numbers, and hyphens")
    private String vendorSlug;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Amount must be at least 1.00")
    private BigDecimal amount;

    @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Invalid phone number format")
    private String customerPhone; // Optional for tracking
}
