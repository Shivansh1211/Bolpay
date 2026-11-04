package com.bolpay.service;

import com.bolpay.entity.Order;
import com.bolpay.gateway.PaymentGateway;
import com.bolpay.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for reconciling payment status with payment gateway
 * Catches lost or delayed webhooks
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final PaymentGateway paymentGateway;

    @Value("${order.reconcile-min-age-seconds:10}")
    private int minimumOrderAgeSeconds;

    /**
     * Scheduled task to reconcile PENDING orders with payment gateway
     * Runs every 20-30 seconds as per spec
     */
    @Scheduled(fixedRateString = "${order.reconciliation-check-ms:25000}")
    @Transactional
    public void reconcilePendingOrders() {
        log.info("Starting reconciliation of pending orders");

        // Get PENDING orders that are older than 10 seconds (give time for webhook)
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(minimumOrderAgeSeconds);
        List<Order> pendingOrders = orderRepository.findByStatusAndCreatedAtBefore(
                Order.OrderStatus.PENDING,
                cutoff
        );

        log.info("Found {} pending orders to reconcile", pendingOrders.size());

        for (Order order : pendingOrders) {
            try {
                reconcileOrder(order);
            } catch (Exception e) {
                log.error("Failed to reconcile order {}", order.getOrderCode(), e);
            }
        }

        log.info("Reconciliation completed");
    }

    /**
     * Reconcile a single order with the payment gateway
     */
    private void reconcileOrder(Order order) {
        if (order.getGatewayRef() == null) {
            log.warn("Order {} has no gateway reference, skipping", order.getOrderCode());
            return;
        }

        log.info("Reconciling order {} with gateway ref {}", order.getOrderCode(), order.getGatewayRef());

        PaymentGateway.PaymentStatus gatewayStatus = paymentGateway.getPaymentStatus(order.getGatewayRef());

        switch (gatewayStatus) {
            case SUCCESS:
                log.info("Order {} is SUCCESS in gateway, marking as PAID", order.getOrderCode());
                orderService.markOrderAsPaid(order.getOrderCode(), order.getGatewayRef());
                break;
            
            case FAILED:
                log.info("Order {} is FAILED in gateway, marking as EXPIRED", order.getOrderCode());
                order.setStatus(Order.OrderStatus.EXPIRED);
                orderRepository.save(order);
                break;
            
            case PENDING:
                log.debug("Order {} is still PENDING in gateway", order.getOrderCode());
                // Keep as PENDING
                break;
            
            case UNKNOWN:
                log.warn("Order {} has UNKNOWN status in gateway", order.getOrderCode());
                // Keep as PENDING, will retry later
                break;
        }
    }

    /**
     * Scheduled task to mark expired orders
     * Runs every 30 seconds
     */
    @Scheduled(fixedRateString = "${order.expiry-check-ms:30000}")
    @Transactional
    public void markExpiredOrders() {
        orderService.markExpiredOrders();
    }

    /**
     * Scheduled task to escalate unresolved expired orders to incident log
     * Runs every minute
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void escalateExpiredOrders() {
        log.info("Checking for orders to escalate to incident log");

        // Get EXPIRED and LATE_PAID orders past escalate_at time
        LocalDateTime now = LocalDateTime.now();
        List<Order> ordersToEscalate = orderRepository.findByStatusAndEscalateAtBefore(Order.OrderStatus.EXPIRED, now);
        ordersToEscalate.addAll(orderRepository.findByStatusAndEscalateAtBefore(Order.OrderStatus.LATE_PAID, now));

        log.info("Found {} orders to escalate", ordersToEscalate.size());

        // In a real implementation, these would be logged to an incident table
        for (Order order : ordersToEscalate) {
            log.warn("Escalating order {} to incident log - status: {}",
                order.getOrderCode(), order.getStatus());
        }
    }
}
