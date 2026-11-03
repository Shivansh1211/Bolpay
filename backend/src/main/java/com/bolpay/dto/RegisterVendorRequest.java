package com.bolpay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for vendor registration request
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterVendorRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian phone number")
    private String phoneNumber;

    @NotBlank(message = "UPI ID is required")
    @Pattern(regexp = "^[\\w.-]+@[\\w.-]+$", message = "Invalid UPI ID format")
    private String upiId;

    @NotBlank(message = "Business name is required")
    @Size(max = 200, message = "Business name must be less than 200 characters")
    private String businessName;

    @Size(max = 100, message = "Slug must be less than 100 characters")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug can only contain lowercase letters, numbers, and hyphens")
    private String slug;

    @Size(max = 1000, message = "Description must be less than 1000 characters")
    private String description;

    @Size(max = 500, message = "Banner URL must be less than 500 characters")
    private String bannerUrl;

    private String menuItems; // JSON string
}
