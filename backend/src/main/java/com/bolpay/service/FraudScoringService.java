package com.bolpay.service;

import com.bolpay.dto.FraudFlagDto;
import com.bolpay.entity.FraudFlag;
import com.bolpay.entity.Order;
import com.bolpay.repository.FraudFlagRepository;
import com.bolpay.repository.OrderRepository;
import com.bolpay.repository.ScanSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudScoringService {
    private final OrderRepository orderRepository;
    private final ScanSessionRepository scanSessionRepository;
    private final FraudFlagRepository fraudFlagRepository;
    private final MlScoringClient mlScoringClient;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${fraud.rule-timeouts-15m:3}")
    private int ruleTimeoutThreshold;

    @Async("fraudScoringExecutor")
    @Transactional
    public void scoreExpiredOrder(Long orderId) {
        try {
            scoreExpiredOrderSynchronously(orderId);
        } catch (Exception e) {
            // Risk review is best-effort and must never interrupt expiry or payment state handling.
            log.warn("Risk review could not be completed for expired order id {}", orderId);
        }
    }

    void scoreExpiredOrderSynchronously(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || (order.getStatus() != Order.OrderStatus.EXPIRED
                && order.getStatus() != Order.OrderStatus.LATE_PAID)) return;
        if (fraudFlagRepository.existsByOrder_Id(orderId)) return;

        LocalDateTime now = LocalDateTime.now();
        Map<String, Double> features = calculateNumericFeatures(order, now);
        MlScoringClient.MlScoreResponse result;
        try {
            result = mlScoringClient.score(features);
            if (!result.suspicious()) return;
            List<String> reasons = safeReasons(result.reasons());
            saveFlag(order, BigDecimal.valueOf(result.score()), reasons,
                    result.modelVersion() == null ? "unknown" : result.modelVersion(),
                    result.scorer() == null || result.scorer().isBlank() ? "ml" : result.scorer());
        } catch (Exception unavailable) {
            long timeouts = features.get("timeouts_per_device_15m").longValue();
            if (timeouts < ruleTimeoutThreshold) return;
            String reason = "Same device timed out " + timeouts
                    + " times in 15 minutes. Look at this order.";
            saveFlag(order, BigDecimal.ONE, List.of(reason), "rule-baseline-v1", "rule");
        }
    }

    Map<String, Double> calculateNumericFeatures(Order order, LocalDateTime now) {
        LocalDateTime createdAt = order.getCreatedAt();
        String deviceHash = order.getDeviceFingerprintHash();
        LocalDateTime priorScanAt = deviceHash == null ? null : scanSessionRepository
                .findFirstByVendor_IdAndDeviceHashAndCreatedAtBeforeOrderByCreatedAtDesc(
                        order.getVendorId(), deviceHash, createdAt)
                .map(scan -> scan.getCreatedAt()).orElse(null);
        double secondsToFirstAttempt = priorScanAt == null ? 0
                : secondsBetween(priorScanAt, createdAt);

        long timeouts15m = countTimeouts(deviceHash, now.minusMinutes(15), now);
        long expired1h = countTimeouts(deviceHash, now.minusHours(1), now);
        long expired24h = countTimeouts(deviceHash, now.minusHours(24), now);
        long pendingVendor10m = orderRepository.countVendorPendingOrders(
                order.getVendorId(), now.minusMinutes(10), now);
        long bulkDevice10m = deviceHash == null ? 0 : orderRepository.countDeviceOrders(
                deviceHash, now.minusMinutes(10), now);

        double amountZScore = vendorAmountZScore(order, now);
        double repeatGap = deviceHash == null ? 0 : orderRepository
                .findFirstByDeviceFingerprintHashAndCreatedAtBeforeOrderByCreatedAtDesc(deviceHash, createdAt)
                .map(previous -> secondsBetween(previous.getCreatedAt(), createdAt)).orElse(0.0);
        double timePending = secondsBetween(createdAt, now);

        // Only numeric features cross the service boundary. Device hashes, order codes,
        // phone numbers, vendor names, and raw browser identifiers are not included.
        return Map.of(
                "seconds_to_first_attempt", secondsToFirstAttempt,
                "time_pending", timePending,
                "expired_orders_per_device_1h", (double) expired1h,
                "expired_orders_per_device_24h", (double) expired24h,
                "timeouts_per_device_15m", (double) timeouts15m,
                "pending_orders_per_vendor_10m", (double) pendingVendor10m,
                "amount_z_score", amountZScore,
                "hour_of_day", (double) createdAt.getHour(),
                "repeat_order_gap_seconds", repeatGap,
                "bulk_orders_per_device_10m", (double) bulkDevice10m);
    }

    @Transactional(readOnly = true)
    public List<FraudFlagDto> getFlagsForVendor(Long vendorId) {
        return fraudFlagRepository.findByOrder_Vendor_IdOrderByCreatedAtDesc(vendorId).stream()
                .filter(FraudFlag::isSuspicious)
                .map(flag -> new FraudFlagDto(flag.getOrder().getOrderCode(), flag.getOrder().getToken(),
                        flag.getOrder().getAmount(), flag.getScore(), flag.getReasons(),
                        flag.getModelVersion(), flag.getScorer(), flag.getCreatedAt()))
                .toList();
    }

    private long countTimeouts(String deviceHash, LocalDateTime since, LocalDateTime until) {
        return deviceHash == null ? 0 : orderRepository.countDeviceTimeouts(deviceHash, since, until);
    }

    private double vendorAmountZScore(Order order, LocalDateTime now) {
        Object[] stats = orderRepository.findVendorAmountNorm(order.getVendorId(), now.minusDays(30), order.getId());
        if (stats == null || stats.length < 2) return 0;
        double mean = numericValue(stats[0]);
        double standardDeviation = numericValue(stats[1]);
        return standardDeviation <= 0 ? 0 : (order.getAmount().doubleValue() - mean) / standardDeviation;
    }

    private double numericValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private double secondsBetween(LocalDateTime start, LocalDateTime end) {
        return Math.max(0, Duration.between(start, end).toMillis() / 1000.0);
    }

    private List<String> safeReasons(List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) return List.of("Unusual payment pattern. Look at this order.");
        return reasons.stream().map(String::trim).filter(reason -> !reason.isBlank()).limit(5).toList();
    }

    private void saveFlag(Order order, BigDecimal score, List<String> reasons, String modelVersion, String scorer) {
        FraudFlag flag = FraudFlag.builder()
                .order(orderRepository.getReferenceById(order.getId()))
                .score(score.setScale(4, RoundingMode.HALF_UP))
                .suspicious(true)
                .reasons(reasons)
                .modelVersion(modelVersion)
                .scorer(scorer)
                .build();
        fraudFlagRepository.save(flag);

        Runnable publish = () -> messagingTemplate.convertAndSend(
                "/topic/vendor/" + order.getVendorId(), Map.of("eventType", "FRAUD_FLAG"));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish.run(); }
            });
        } else {
            publish.run();
        }
    }
}
