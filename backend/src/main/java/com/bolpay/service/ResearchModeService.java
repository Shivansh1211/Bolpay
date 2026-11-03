package com.bolpay.service;

import com.bolpay.dto.ResearchSessionDto;
import com.bolpay.entity.ResearchSession;
import com.bolpay.repository.OrderRepository;
import com.bolpay.repository.ResearchSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResearchModeService {

    private final ResearchSessionRepository researchSessionRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public ResearchSessionDto createSession(Long vendorId, String label) {
        ResearchSession session = ResearchSession.builder()
                .vendorId(vendorId)
                .label(label)
                .startTime(LocalDateTime.now())
                .totalOrders(0)
                .paidOrders(0)
                .expiredOrders(0)
                .flaggedOrders(0)
                .build();
        
        session = researchSessionRepository.save(session);
        log.info("Created research session: id={}, vendorId={}, label={}", session.getId(), vendorId, label);
        return mapToDto(session);
    }

    public Map<String, Object> getReport(Long vendorId) {
        List<ResearchSession> sessions = researchSessionRepository.findByVendorIdOrderByStartTimeDesc(vendorId);
        
        int totalSessions = sessions.size();
        int honestSessions = (int) sessions.stream().filter(s -> "honest".equals(s.getLabel())).count();
        int fakeSessions = (int) sessions.stream().filter(s -> "simulated_fake".equals(s.getLabel())).count();
        
        int totalOrders = sessions.stream().mapToInt(s -> s.getTotalOrders() != null ? s.getTotalOrders() : 0).sum();
        int totalPaid = sessions.stream().mapToInt(s -> s.getPaidOrders() != null ? s.getPaidOrders() : 0).sum();
        int totalExpired = sessions.stream().mapToInt(s -> s.getExpiredOrders() != null ? s.getExpiredOrders() : 0).sum();
        int totalFlagged = sessions.stream().mapToInt(s -> s.getFlaggedOrders() != null ? s.getFlaggedOrders() : 0).sum();
        
        Map<String, Object> report = new HashMap<>();
        report.put("totalSessions", totalSessions);
        report.put("honestSessions", honestSessions);
        report.put("fakeSessions", fakeSessions);
        report.put("totalOrders", totalOrders);
        report.put("totalPaid", totalPaid);
        report.put("totalExpired", totalExpired);
        report.put("totalFlagged", totalFlagged);
        report.put("paymentRate", totalOrders > 0 ? (double) totalPaid / totalOrders : 0);
        report.put("sessions", sessions.stream().map(this::mapToDto).toList());
        
        return report;
    }

    public String exportToCsv(Long vendorId) {
        List<ResearchSession> sessions = researchSessionRepository.findByVendorIdOrderByStartTimeDesc(vendorId);
        
        StringBuilder csv = new StringBuilder();
        csv.append("id,vendorId,label,startTime,endTime,totalOrders,paidOrders,expiredOrders,flaggedOrders\n");
        
        for (ResearchSession session : sessions) {
            csv.append(session.getId()).append(",");
            csv.append(session.getVendorId()).append(",");
            csv.append(session.getLabel()).append(",");
            csv.append(session.getStartTime()).append(",");
            csv.append(session.getEndTime() != null ? session.getEndTime() : "").append(",");
            csv.append(session.getTotalOrders() != null ? session.getTotalOrders() : 0).append(",");
            csv.append(session.getPaidOrders() != null ? session.getPaidOrders() : 0).append(",");
            csv.append(session.getExpiredOrders() != null ? session.getExpiredOrders() : 0).append(",");
            csv.append(session.getFlaggedOrders() != null ? session.getFlaggedOrders() : 0);
            csv.append("\n");
        }
        
        return csv.toString();
    }

    private ResearchSessionDto mapToDto(ResearchSession session) {
        return ResearchSessionDto.builder()
                .id(session.getId())
                .vendorId(session.getVendorId())
                .label(session.getLabel())
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .totalOrders(session.getTotalOrders())
                .paidOrders(session.getPaidOrders())
                .expiredOrders(session.getExpiredOrders())
                .flaggedOrders(session.getFlaggedOrders())
                .build();
    }
}
