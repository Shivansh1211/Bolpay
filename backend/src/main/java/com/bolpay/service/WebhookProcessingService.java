package com.bolpay.service;

import com.bolpay.entity.Order;
import com.bolpay.entity.WebhookEvent;
import com.bolpay.repository.OrderRepository;
import com.bolpay.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class WebhookProcessingService {
    private final OrderRepository orderRepository;
    private final WebhookEventRepository eventRepository;
    private final OrderService orderService;

    @Transactional
    public void process(String eventId, String rawPayload, String orderCode, BigDecimal amount,
            String status, String gatewayRef) {
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        WebhookEvent event = WebhookEvent.builder().eventId(eventId).order(order).orderId(order.getId())
                .gateway("mock").payload(rawPayload).status(WebhookEvent.WebhookStatus.REJECTED).build();
        eventRepository.saveAndFlush(event);

        if (order.getAmount().compareTo(amount) != 0) {
            event.setStatus(WebhookEvent.WebhookStatus.FLAGGED);
            throw new IllegalArgumentException("Amount mismatch");
        }
        if ("success".equalsIgnoreCase(status) || "paid".equalsIgnoreCase(status)) {
            if (order.getExpiresAt().isBefore(LocalDateTime.now())) {
                orderService.markOrderAsPaid(orderCode, gatewayRef);
                event.setStatus(WebhookEvent.WebhookStatus.FLAGGED);
            } else {
                orderService.markOrderAsPaid(orderCode, gatewayRef);
                event.setStatus(WebhookEvent.WebhookStatus.PROCESSED);
            }
        }
    }
}
