package com.bolpay.service;

import com.bolpay.entity.Order;
import com.bolpay.gateway.PaymentGateway;
import com.bolpay.repository.OrderRepository;
import com.bolpay.repository.VendorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderTokenAllocationTest {
    @Test
    void usesThreeDigitTokenOnlyAfterEveryTwoDigitTokenIsOccupied() {
        OrderRepository orders = mock(OrderRepository.class);
        when(orders.findByVendorIdAndTokenAndStatus(anyLong(), anyString(), eq(Order.OrderStatus.PENDING)))
                .thenAnswer(call -> call.<String>getArgument(1).length() == 4
                        ? Optional.empty() : Optional.of(new Order()));
        OrderService service = new OrderService(orders, mock(VendorRepository.class),
                mock(PaymentGateway.class), mock(SimpMessagingTemplate.class), mock(FraudScoringService.class));

        String token = service.allocateActiveToken(42L);

        assertTrue(token.matches("[A-HJ-NP-Z][2-9]{3}"));
        verify(orders, times(24 * 8 * 8)).findByVendorIdAndTokenAndStatus(
                eq(42L), argThat(candidate -> candidate.length() == 3), eq(Order.OrderStatus.PENDING));
        verify(orders, atLeastOnce()).findByVendorIdAndTokenAndStatus(
                eq(42L), argThat(candidate -> candidate.length() == 4), eq(Order.OrderStatus.PENDING));
    }

    @Test
    void usesTwoDigitTokenWhenOneIsAvailable() {
        OrderRepository orders = mock(OrderRepository.class);
        when(orders.findByVendorIdAndTokenAndStatus(anyLong(), anyString(), eq(Order.OrderStatus.PENDING)))
                .thenReturn(Optional.empty());
        OrderService service = new OrderService(orders, mock(VendorRepository.class),
                mock(PaymentGateway.class), mock(SimpMessagingTemplate.class), mock(FraudScoringService.class));

        assertTrue(service.allocateActiveToken(42L).matches("[A-HJ-NP-Z][2-9]{2}"));
        verify(orders, never()).findByVendorIdAndTokenAndStatus(
                anyLong(), argThat(candidate -> candidate.length() == 4), eq(Order.OrderStatus.PENDING));
    }
}
