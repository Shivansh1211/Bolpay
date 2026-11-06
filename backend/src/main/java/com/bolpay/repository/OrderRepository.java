package com.bolpay.repository;

import com.bolpay.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

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

    Optional<Order> findFirstByDeviceFingerprintHashAndCreatedAtBeforeOrderByCreatedAtDesc(
            String deviceFingerprintHash, LocalDateTime before);

    @Query(value = "SELECT COUNT(*) FROM orders WHERE device_fingerprint_hash = :deviceHash "
            + "AND status IN ('EXPIRED', 'LATE_PAID') AND created_at >= :since AND created_at < :until",
            nativeQuery = true)
    long countDeviceTimeouts(@Param("deviceHash") String deviceHash,
                             @Param("since") LocalDateTime since,
                             @Param("until") LocalDateTime until);

    @Query(value = "SELECT COUNT(*) FROM orders WHERE device_fingerprint_hash = :deviceHash "
            + "AND created_at >= :since AND created_at < :until", nativeQuery = true)
    long countDeviceOrders(@Param("deviceHash") String deviceHash,
                           @Param("since") LocalDateTime since,
                           @Param("until") LocalDateTime until);

    @Query(value = "SELECT COUNT(*) FROM orders WHERE vendor_id = :vendorId AND status = 'PENDING' "
            + "AND created_at >= :since AND created_at < :until", nativeQuery = true)
    long countVendorPendingOrders(@Param("vendorId") Long vendorId,
                                  @Param("since") LocalDateTime since,
                                  @Param("until") LocalDateTime until);

    @Query(value = "SELECT COALESCE(AVG(amount), 0), COALESCE(STDDEV_POP(amount), 0) FROM orders "
            + "WHERE vendor_id = :vendorId AND created_at >= :since AND id <> :excludeOrderId",
            nativeQuery = true)
    Object[] findVendorAmountNorm(@Param("vendorId") Long vendorId,
                                  @Param("since") LocalDateTime since,
                                  @Param("excludeOrderId") Long excludeOrderId);

    List<Order> findByPayerHashAndCreatedAtAfter(String payerHash, LocalDateTime after);

    Optional<Order> findByVendorIdAndTokenAndStatus(Long vendorId, String token, Order.OrderStatus status);

    Optional<Order> findByVendorIdAndOrderCode(Long vendorId, String orderCode);

    List<Order> findByStatusAndEscalateAtBefore(Order.OrderStatus status, LocalDateTime now);
}
