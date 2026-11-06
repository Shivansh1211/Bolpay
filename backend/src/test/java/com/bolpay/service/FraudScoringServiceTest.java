package com.bolpay.service;

import com.bolpay.entity.FraudFlag;
import com.bolpay.entity.Order;
import com.bolpay.repository.FraudFlagRepository;
import com.bolpay.repository.OrderRepository;
import com.bolpay.repository.ScanSessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FraudScoringServiceTest {
    @Test
    void availableMlCreatesReviewFlagWithoutChangingOrderStatus() throws Exception {
        Fixture f = new Fixture();
        AtomicReference<String> postedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/score", exchange -> {
            postedBody.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            byte[] response = "{\"anomaly_score\":0.42,\"is_suspicious\":true,\"reasons\":[\"Unusual payment pattern. Look at this order.\"],\"model_version\":\"2.0.0\",\"scorer\":\"isolation_forest\"}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            MlScoringClient httpClient = new MlScoringClient(new ObjectMapper());
            ReflectionTestUtils.setField(httpClient, "serviceUrl", "http://127.0.0.1:" + server.getAddress().getPort());
            ReflectionTestUtils.setField(httpClient, "apiKey", "test-only-key");
            FraudScoringService service = new FraudScoringService(f.orders, f.scans, f.flags, httpClient,
                    mock(SimpMessagingTemplate.class));
            ReflectionTestUtils.setField(service, "ruleTimeoutThreshold", 3);

            service.scoreExpiredOrderSynchronously(f.order.getId());

            FraudFlag flag = f.savedFlag();
            assertEquals("isolation_forest", flag.getScorer());
            assertEquals("2.0.0", flag.getModelVersion());
            assertEquals(Order.OrderStatus.EXPIRED, f.order.getStatus());
            verify(f.orders, never()).save(any(Order.class));
            assertTrue(postedBody.get().contains("\"features\""));
            assertFalse(postedBody.get().contains("ORDER123"));
            assertFalse(postedBody.get().contains("hashed-device-only"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unavailableMlUsesThreeTimeoutRuleAndLeavesPaymentFlowUntouched() throws Exception {
        Fixture f = new Fixture();
        when(f.client.score(anyMap())).thenThrow(new IOException("offline"));
        when(f.orders.countDeviceTimeouts(eq(f.order.getDeviceFingerprintHash()), any(), any())).thenReturn(3L);

        f.service.scoreExpiredOrderSynchronously(f.order.getId());

        FraudFlag flag = f.savedFlag();
        assertEquals("rule", flag.getScorer());
        assertEquals("rule-baseline-v1", flag.getModelVersion());
        assertTrue(flag.getReasons().get(0).contains("timed out 3 times in 15 minutes"));
        assertEquals(Order.OrderStatus.EXPIRED, f.order.getStatus());
        verify(f.orders, never()).save(any(Order.class));
    }

    @Test
    void slowMlTimesOutIntoRuleFallback() throws Exception {
        Fixture f = new Fixture();
        when(f.client.score(anyMap())).thenThrow(new HttpTimeoutException("timed out"));
        when(f.orders.countDeviceTimeouts(eq(f.order.getDeviceFingerprintHash()), any(), any())).thenReturn(3L);

        f.service.scoreExpiredOrderSynchronously(f.order.getId());

        assertEquals("rule", f.savedFlag().getScorer());
        assertEquals(Order.OrderStatus.EXPIRED, f.order.getStatus());
    }

    @Test
    void scoringRequestContainsOnlyNumericFeaturesAndUsesApiKeyHeader() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/score", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            apiKey.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            byte[] response = "{\"anomaly_score\":0.1,\"is_suspicious\":false,\"reasons\":[],\"model_version\":\"2.0.0\",\"scorer\":\"isolation_forest\"}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            MlScoringClient client = new MlScoringClient(new ObjectMapper());
            ReflectionTestUtils.setField(client, "serviceUrl", "http://127.0.0.1:" + server.getAddress().getPort());
            ReflectionTestUtils.setField(client, "apiKey", "test-only-key");
            client.score(Map.of("amount_z_score", 2.0, "timeouts_per_device_15m", 3.0));

            JsonNode root = new ObjectMapper().readTree(body.get());
            assertEquals("test-only-key", apiKey.get());
            assertEquals(2, root.path("features").size());
            root.path("features").elements().forEachRemaining(value -> assertTrue(value.isNumber()));
            assertFalse(body.get().contains("orderCode"));
            assertFalse(body.get().contains("deviceHash"));
            assertFalse(body.get().contains("phone"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void scoringClientEnforcesItsShortRequestTimeout() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/score", exchange -> {
            try { Thread.sleep(1200); } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            try {
                exchange.sendResponseHeaders(200, 2);
                exchange.getResponseBody().write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // Expected: the client has already timed out and closed the socket.
            } finally { exchange.close(); }
        });
        server.start();
        try {
            MlScoringClient client = new MlScoringClient(new ObjectMapper());
            ReflectionTestUtils.setField(client, "serviceUrl", "http://127.0.0.1:" + server.getAddress().getPort());
            ReflectionTestUtils.setField(client, "apiKey", "test-only-key");
            // The endpoint deliberately takes longer than the client's configured
            // 500 ms request timeout. Assert the timeout signal itself, avoiding
            // brittle wall-clock ceilings on loaded CI/Docker hosts.
            assertThrows(HttpTimeoutException.class,
                    () -> client.score(Map.of("timeouts_per_device_15m", 3.0)));
        } finally {
            server.stop(0);
        }
    }

    private static class Fixture {
        final OrderRepository orders = mock(OrderRepository.class);
        final ScanSessionRepository scans = mock(ScanSessionRepository.class);
        final FraudFlagRepository flags = mock(FraudFlagRepository.class);
        final MlScoringClient client = mock(MlScoringClient.class);
        final Order order = Order.builder().id(7L).vendorId(9L).status(Order.OrderStatus.EXPIRED)
                .amount(new BigDecimal("21.00")).orderCode("ORDER123").token("J99")
                .createdAt(LocalDateTime.now().minusMinutes(3)).deviceFingerprintHash("hashed-device-only")
                .build();
        final FraudScoringService service = new FraudScoringService(orders, scans, flags, client,
                mock(SimpMessagingTemplate.class));

        Fixture() {
            ReflectionTestUtils.setField(service, "ruleTimeoutThreshold", 3);
            when(orders.findById(7L)).thenReturn(Optional.of(order));
            when(scans.findFirstByVendor_IdAndDeviceHashAndCreatedAtBeforeOrderByCreatedAtDesc(anyLong(), anyString(), any()))
                    .thenReturn(Optional.empty());
            when(orders.findFirstByDeviceFingerprintHashAndCreatedAtBeforeOrderByCreatedAtDesc(anyString(), any()))
                    .thenReturn(Optional.empty());
            when(orders.findVendorAmountNorm(anyLong(), any(), anyLong())).thenReturn(new Object[]{21.0, 4.0});
            when(orders.getReferenceById(7L)).thenReturn(order);
            when(flags.save(any(FraudFlag.class))).thenAnswer(invocation -> invocation.getArgument(0));
        }

        FraudFlag savedFlag() {
            var captor = org.mockito.ArgumentCaptor.forClass(FraudFlag.class);
            verify(flags).save(captor.capture());
            return captor.getValue();
        }
    }
}
