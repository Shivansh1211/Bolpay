package com.bolpay.repository;

import com.bolpay.entity.IncidentLog;
import com.bolpay.entity.IncidentLog.IncidentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface IncidentLogRepository extends JpaRepository<IncidentLog, Long> {

    List<IncidentLog> findByVendorIdAndResolvedOrderByCreatedAtDesc(Long vendorId, Boolean resolved);

    List<IncidentLog> findByVendorIdAndIncidentTypeAndResolvedOrderByCreatedAtDesc(
        Long vendorId, IncidentType incidentType, Boolean resolved);

    List<IncidentLog> findByCreatedAtAfter(LocalDateTime after);
}
