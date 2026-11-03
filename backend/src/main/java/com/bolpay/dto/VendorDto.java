package com.bolpay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Vendor entity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorDto {

    private Long id;
    private String phoneNumber;
    private String upiId;
    private String businessName;
    private String slug;
    private String description;
    private String bannerUrl;
    private String menuItems;
    private boolean isActive;
}
