package com.bolpay.repository;

import com.bolpay.entity.FraudFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FraudFlagRepository extends JpaRepository<FraudFlag, Long> {
    List<FraudFlag> findByOrder_Vendor_IdOrderByCreatedAtDesc(Long vendorId);
    boolean existsByOrder_Id(Long orderId);
}
