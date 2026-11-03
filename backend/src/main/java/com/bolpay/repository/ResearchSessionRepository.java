package com.bolpay.repository;

import com.bolpay.entity.ResearchSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ResearchSessionRepository extends JpaRepository<ResearchSession, Long> {

    List<ResearchSession> findByVendorIdOrderByStartTimeDesc(Long vendorId);

    List<ResearchSession> findByVendorIdAndLabelOrderByStartTimeDesc(Long vendorId, String label);
}
