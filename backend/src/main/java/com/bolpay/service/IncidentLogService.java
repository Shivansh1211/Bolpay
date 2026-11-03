package com.bolpay.service;

import com.bolpay.entity.IncidentLog;
import com.bolpay.entity.IncidentLog.IncidentType;
import com.bolpay.entity.Order;
import com.bolpay.repository.IncidentLogRepository;
import com.bolpay.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentLogService {

    private final IncidentLogRepository incidentLogRepository;
    private final OrderRepository orderRepository;

    /**
     * Create a new incident log entry
     */
    @Transactional
    public IncidentLog createIncident(Long orderId, Long vendorId, IncidentType incidentType, String description) {
        Order order = orderRepository.findById(orderId).orElse(null);
        
        IncidentLog incident = IncidentLog.builder()
                .order(order)
                .orderId(orderId)
                .vendorId(vendorId)
                .incidentType(incidentType)
                .description(description)
                .resolved(false)
                .build();
        
        incident = incidentLogRepository.save(incident);
        log.info("Created incident: type={}, orderId={}, vendorId={}", incidentType, orderId, vendorId);
        return incident;
    }

    /**
     * Get unresolved incidents for a vendor
     */
    public List<IncidentLog> getUnresolvedIncidents(Long vendorId) {
        return incidentLogRepository.findByVendorIdAndResolvedOrderByCreatedAtDesc(vendorId, false);
    }

    /**
     * Get all incidents for a vendor
     */
    public List<IncidentLog> getIncidentsByVendor(Long vendorId) {
        return incidentLogRepository.findByVendorIdAndResolvedOrderByCreatedAtDesc(vendorId, false);
    }

    /**
     * Resolve an incident
     */
    @Transactional
    public void resolveIncident(Long incidentId) {
        IncidentLog incident = incidentLogRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found"));
        incident.setResolved(true);
        incident.setResolvedAt(LocalDateTime.now());
        incidentLogRepository.save(incident);
        log.info("Resolved incident: id={}", incidentId);
    }
}
