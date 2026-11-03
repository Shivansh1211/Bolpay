package com.bolpay.repository;

import com.bolpay.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Order entity
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderCode(String orderCode);

    List<Order> findByVendorIdOrderByCreatedAtDesc(Long vendorId);

    List<Order> findByVendorIdAndStatusOrderByCreatedAtDesc(Long vendorId, Order.OrderStatus status);

    List<Order> findByStatusAndExpiresAtBefore(Order.OrderStatus status, LocalDateTime now);

    List<Order> findByStatusAndCreatedAtAfter(Order.OrderStatus status, LocalDateTime after);

    List<Order> findByStatusAndCreatedAtBefore(Order.OrderStatus status, LocalDateTime before);

    List<Order> findByVendorIdAndCreatedAtAfter(Long vendorId, LocalDateTime after);

    List<Order> findByDeviceFingerprintHashAndCreatedAtAfter(String deviceFingerprintHash, LocalDateTime after);

    List<Order> findByPayerHashAndCreatedAtAfter(String payerHash, LocalDateTime after);

    Optional<Order> findByVendorIdAndTokenAndStatus(Long vendorId, String token, Order.OrderStatus status);

    List<Order> findByStatusAndEscalateAtBefore(Order.OrderStatus status, LocalDateTime now);
}
