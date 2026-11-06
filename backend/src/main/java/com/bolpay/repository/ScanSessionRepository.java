package com.bolpay.repository;

import com.bolpay.entity.ScanSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ScanSessionRepository extends JpaRepository<ScanSession, Long> {
    long countByVendor_IdAndCreatedAtAfter(Long vendorId, LocalDateTime after);

    Optional<ScanSession> findFirstByVendor_IdAndDeviceHashAndCreatedAtBeforeOrderByCreatedAtDesc(
            Long vendorId, String deviceHash, LocalDateTime before);
}
