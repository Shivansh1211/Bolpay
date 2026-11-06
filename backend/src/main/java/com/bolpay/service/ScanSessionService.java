package com.bolpay.service;

import com.bolpay.entity.ScanSession;
import com.bolpay.entity.Vendor;
import com.bolpay.repository.ScanSessionRepository;
import com.bolpay.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ScanSessionService {
    private final ScanSessionRepository scanSessionRepository;
    private final VendorRepository vendorRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void recordScan(String slug, String deviceId) {
        if (deviceId == null || deviceId.isBlank() || deviceId.length() > 200) {
            throw new IllegalArgumentException("A valid device identifier is required");
        }
        Vendor vendor = vendorRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found"));
        scanSessionRepository.save(ScanSession.builder()
                .vendor(vendor)
                .deviceHash(sha256(deviceId))
                .build());
        Runnable publish = () -> messagingTemplate.convertAndSend("/topic/vendor/" + vendor.getId(),
                java.util.Map.of("eventType", "SCAN"));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish.run(); }
            });
        } else {
            publish.run();
        }
    }

    @Transactional(readOnly = true)
    public long countRecentScans(Long vendorId, LocalDateTime since) {
        return scanSessionRepository.countByVendor_IdAndCreatedAtAfter(vendorId, since);
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
