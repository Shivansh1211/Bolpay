package com.bolpay.repository;

import com.bolpay.entity.ScanSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface ScanSessionRepository extends JpaRepository<ScanSession, Long> {
    long countByVendor_IdAndCreatedAtAfter(Long vendorId, LocalDateTime after);
}
