package com.bolpay.service;

import com.bolpay.dto.RegisterVendorRequest;
import com.bolpay.dto.VendorDto;
import com.bolpay.entity.Vendor;
import com.bolpay.repository.VendorRepository;
import com.bolpay.util.Sanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for vendor operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VendorService {

    private final VendorRepository vendorRepository;

    /**
     * Register a new vendor
     */
    @Transactional
    public VendorDto registerVendor(RegisterVendorRequest request) {
        // Check if phone number already exists
        if (vendorRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new IllegalArgumentException("Phone number already registered");
        }

        // Check if slug already exists
        if (vendorRepository.existsBySlug(request.getSlug())) {
            throw new IllegalArgumentException("Slug already taken");
        }

        // Generate slug if not provided
        String slug = request.getSlug();
        if (slug == null || slug.isEmpty()) {
            slug = generateSlugFromBusinessName(request.getBusinessName());
            // Ensure uniqueness
            int counter = 1;
            while (vendorRepository.existsBySlug(slug)) {
                slug = generateSlugFromBusinessName(request.getBusinessName()) + "-" + counter++;
            }
        }

        // Sanitize user input to prevent XSS
        Vendor vendor = Vendor.builder()
                .phoneNumber(request.getPhoneNumber())
                .upiId(Sanitizer.sanitize(request.getUpiId(), 100))
                .businessName(Sanitizer.sanitize(request.getBusinessName(), 200))
                .slug(slug)
                .description(Sanitizer.sanitize(request.getDescription(), 2000))
                .bannerUrl(Sanitizer.sanitize(request.getBannerUrl(), 500))
                .menuItems(request.getMenuItems()) // JSON string, validated separately
                .isActive(true)
                .build();

        vendor = vendorRepository.save(vendor);
        log.info("Registered new vendor: {} with slug: {}", vendor.getBusinessName(), vendor.getSlug());

        return mapToDto(vendor);
    }

    /**
     * Get vendor by phone number
     */
    public VendorDto getVendorByPhoneNumber(String phoneNumber) {
        return vendorRepository.findByPhoneNumber(phoneNumber)
                .map(this::mapToDto)
                .orElse(null);
    }

    /**
     * Get vendor by slug (for public pages)
     */
    public VendorDto getVendorBySlug(String slug) {
        return vendorRepository.findBySlug(slug)
                .map(this::mapToDto)
                .orElse(null);
    }

    /**
     * Update vendor profile
     */
    @Transactional
    public VendorDto updateVendor(Long vendorId, RegisterVendorRequest request) {
        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found"));

        // Update fields (only if provided)
        if (request.getUpiId() != null) {
            vendor.setUpiId(request.getUpiId());
        }
        if (request.getBusinessName() != null) {
            vendor.setBusinessName(request.getBusinessName());
        }
        if (request.getDescription() != null) {
            vendor.setDescription(request.getDescription());
        }
        if (request.getBannerUrl() != null) {
            vendor.setBannerUrl(request.getBannerUrl());
        }
        if (request.getMenuItems() != null) {
            vendor.setMenuItems(request.getMenuItems());
        }

        vendor = vendorRepository.save(vendor);
        log.info("Updated vendor: {}", vendor.getBusinessName());

        return mapToDto(vendor);
    }

    /**
     * Generate URL-friendly slug from business name
     */
    private String generateSlugFromBusinessName(String businessName) {
        return businessName.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    /**
     * Map Vendor entity to DTO
     */
    private VendorDto mapToDto(Vendor vendor) {
        return VendorDto.builder()
                .id(vendor.getId())
                .phoneNumber(vendor.getPhoneNumber())
                .upiId(vendor.getUpiId())
                .businessName(vendor.getBusinessName())
                .slug(vendor.getSlug())
                .description(vendor.getDescription())
                .bannerUrl(vendor.getBannerUrl())
                .menuItems(vendor.getMenuItems())
                .isActive(vendor.getIsActive())
                .build();
    }
}
