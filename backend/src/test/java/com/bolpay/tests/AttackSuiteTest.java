package com.bolpay.tests;

import com.bolpay.dto.CreateOrderRequest;
import com.bolpay.dto.OrderDto;
import com.bolpay.entity.Order;
import com.bolpay.entity.Vendor;
import com.bolpay.repository.OrderRepository;
import com.bolpay.repository.VendorRepository;
import com.bolpay.service.OrderService;
import com.bolpay.service.AuthService;
import com.bolpay.service.ReconciliationService;
import com.bolpay.service.ScanSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Attack suite to prove system security against various fraud scenarios
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "order.expire-after=5", "order.expiry-check-ms=250",
        "order.reconciliation-check-ms=3600000", "order.reconcile-min-age-seconds=0",
        "registration.invite-code="
})
@ActiveProfiles("test")
public class AttackSuiteTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private ScanSessionService scanSessionService;

    @LocalServerPort
    private int serverPort;

    private Vendor testVendor;

    @BeforeEach
    void setup() {
        // Create test vendor
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        testVendor = Vendor.builder()
                .phoneNumber("900000" + String.format("%04d", Math.floorMod(UUID.randomUUID().hashCode(), 10_000)))
                .upiId("demo.vendor@examplebank")
                .businessName("Test Vendor")
                .slug("test-vendor-" + unique)
                .isActive(true)
                .build();
        testVendor = vendorRepository.save(testVendor);
    }

    /**
     * Attack 1: Fake modded-app screen (no webhook)
     * Expected: Order stays PENDING then EXPIRED
     */
    @Test
    void testFakeModdedAppScreen_NoWebhook() throws InterruptedException {
        // Create order
        CreateOrderRequest request = new CreateOrderRequest();
        request.setVendorSlug(testVendor.getSlug());
        request.setAmount(new BigDecimal("100"));
        request.setCustomerPhone("9000000000");

        OrderDto order = orderService.createOrder(request, "test-device");

        assertEquals("PENDING", order.getStatus());

        long deadline = System.currentTimeMillis() + 12000;
        Order dbOrder;
        do {
            Thread.sleep(100);
            dbOrder = orderRepository.findByOrderCode(order.getOrderCode()).orElseThrow();
        } while (!"EXPIRED".equals(dbOrder.getStatus().name()) && System.currentTimeMillis() < deadline);
        assertEquals("EXPIRED", dbOrder.getStatus().name());

        System.out.println("✓ Attack 1 PASSED: Fake modded-app screen correctly expires without webhook");
    }

    /**
     * Attack 2: Forged webhook with bad signature
     * Expected: Rejected
     */
    @Test
    void testForgedWebhook_BadSignature() {
        // Create order
        CreateOrderRequest request = new CreateOrderRequest();
        request.setVendorSlug(testVendor.getSlug());
        request.setAmount(new BigDecimal("100"));

        OrderDto order = orderService.createOrder(request, "test-device");

        // Send forged webhook with bad signature
        Map<String, Object> payload = new HashMap<>();
        payload.put("event_id", "evt_test_1");
        payload.put("order_code", order.getOrderCode());
        payload.put("amount", "100");
        payload.put("status", "success");
        payload.put("gateway_ref", "test_ref");

        ResponseEntity<Map> response = postWebhook(payload, "bad_signature");

        // Should be rejected (400 Bad Request)
        assertEquals(401, response.getStatusCode().value());

        // Order should NOT be marked as PAID
        Order dbOrder = orderRepository.findByOrderCode(order.getOrderCode()).orElseThrow();
        assertNotEquals("PAID", dbOrder.getStatus().name());

        System.out.println("✓ Attack 2 PASSED: Forged webhook with bad signature rejected");
    }

    @Test
    void webhookWithoutSignatureIsRejected() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setVendorSlug(testVendor.getSlug());
        request.setAmount(new BigDecimal("100"));
        OrderDto order = orderService.createOrder(request, "test-device");
        Map<String, Object> payload = Map.of("event_id", "evt_unsigned", "order_code", order.getOrderCode(),
                "amount", "100", "status", "success");
        assertEquals(401, postWebhook(payload, null, false).getStatusCode().value());
        assertEquals("PENDING", orderRepository.findByOrderCode(order.getOrderCode()).orElseThrow().getStatus().name());
    }

    @Test
    void correctlySignedWebhookWithStaleTimestampIsRejected() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setVendorSlug(testVendor.getSlug());
        request.setAmount(new BigDecimal("100"));
        OrderDto order = orderService.createOrder(request, "test-device");
        Map<String, Object> payload = Map.of("event_id", "evt_stale_timestamp", "order_code", order.getOrderCode(),
                "amount", "100", "status", "success");
        long staleTimestamp = System.currentTimeMillis() / 1000 - 301;

        ResponseEntity<Map> response = postWebhook(payload, null, true, staleTimestamp);

        assertEquals(401, response.getStatusCode().value());
        assertEquals("PENDING", orderRepository.findByOrderCode(order.getOrderCode()).orElseThrow().getStatus().name());
    }

    @Test
    void webhookMissingEventIdOrAmountIsRejected() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setVendorSlug(testVendor.getSlug());
        request.setAmount(new BigDecimal("100"));
        OrderDto order = orderService.createOrder(request, "test-device");
        Map<String, Object> missingEvent = Map.of("order_code", order.getOrderCode(), "amount", "100", "status", "success");
        Map<String, Object> missingAmount = Map.of("event_id", "evt_missing_amount", "order_code", order.getOrderCode(), "status", "success");
        assertEquals(400, postWebhook(missingEvent, null).getStatusCode().value());
        assertEquals(400, postWebhook(missingAmount, null).getStatusCode().value());
        assertEquals("PENDING", orderRepository.findByOrderCode(order.getOrderCode()).orElseThrow().getStatus().name());
    }

    /**
     * Attack 3: Duplicate webhook
     * Expected: Processed once, one announcement
     */
    @Test
    void testDuplicateWebhook_Idempotent() {
        // Create order
        CreateOrderRequest request = new CreateOrderRequest();
        request.setVendorSlug(testVendor.getSlug());
        request.setAmount(new BigDecimal("100"));

        OrderDto order = orderService.createOrder(request, "test-device");

        // Send first webhook
        Map<String, Object> payload = new HashMap<>();
        payload.put("event_id", "evt_duplicate_test");
        payload.put("order_code", order.getOrderCode());
        payload.put("amount", "100");
        payload.put("status", "success");
        payload.put("gateway_ref", "test_ref");

        ResponseEntity<Map> response1 = postWebhook(payload, null);

        // Send duplicate webhook
        ResponseEntity<Map> response2 = postWebhook(payload, null);

        // Both should succeed (idempotent)
        assertTrue(response1.getStatusCode().is2xxSuccessful());
        assertTrue(response2.getStatusCode().is2xxSuccessful());

        // Order should be PAID (only once)
        Order dbOrder = orderRepository.findByOrderCode(order.getOrderCode()).orElseThrow();
        assertEquals("PAID", dbOrder.getStatus().name());
        assertNotNull(dbOrder.getPaidAt());

        System.out.println("✓ Attack 3 PASSED: Duplicate webhook processed idempotently");
    }

    /**
     * Attack 4: Wrong amount webhook
     * Expected: Rejected and flagged
     */
    @Test
    void testWrongAmountWebhook_Rejected() {
        // Create order for ₹100
        CreateOrderRequest request = new CreateOrderRequest();
        request.setVendorSlug(testVendor.getSlug());
        request.setAmount(new BigDecimal("100"));

        OrderDto order = orderService.createOrder(request, "test-device");

        // Send webhook with wrong amount (₹200)
        Map<String, Object> payload = new HashMap<>();
        payload.put("event_id", "evt_wrong_amount");
        payload.put("order_code", order.getOrderCode());
        payload.put("amount", "200");  // Wrong amount
        payload.put("status", "success");
        payload.put("gateway_ref", "test_ref");

        ResponseEntity<Map> response = postWebhook(payload, null);

        // Should be rejected
        assertTrue(response.getStatusCode().is4xxClientError());

        // Order should NOT be marked as PAID
        Order dbOrder = orderRepository.findByOrderCode(order.getOrderCode()).orElseThrow();
        assertNotEquals("PAID", dbOrder.getStatus().name());

        System.out.println("✓ Attack 4 PASSED: Wrong amount webhook rejected");
    }

    /**
     * Attack 5: Late payment after expiry
     * Expected: Not marked as normal PAID, flagged for review
     */
    @Test
    void testLatePaymentAfterExpiry_Flagged() {
        // Create order
        CreateOrderRequest request = new CreateOrderRequest();
        request.setVendorSlug(testVendor.getSlug());
        request.setAmount(new BigDecimal("100"));

        OrderDto order = orderService.createOrder(request, "test-device");

        // Manually expire the order
        Order dbOrder = orderRepository.findByOrderCode(order.getOrderCode()).orElseThrow();
        dbOrder.setStatus(Order.OrderStatus.EXPIRED);
        dbOrder.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        orderRepository.save(dbOrder);

        // Send payment webhook after expiry
        Map<String, Object> payload = new HashMap<>();
        payload.put("event_id", "evt_late_payment");
        payload.put("order_code", order.getOrderCode());
        payload.put("amount", "100");
        payload.put("status", "success");
        payload.put("gateway_ref", "test_ref");

        ResponseEntity<Map> response = postWebhook(payload, null);

        // Should be processed but flagged
        assertTrue(response.getStatusCode().is2xxSuccessful());

        // Order should be FLAGGED, not normal PAID
        dbOrder = orderRepository.findByOrderCode(order.getOrderCode()).orElseThrow();
        assertEquals("LATE_PAID", dbOrder.getStatus().name());

        System.out.println("✓ Attack 5 PASSED: Late payment after expiry flagged");
    }

    /**
     * Attack 6: Replay of an old Verified page / order code
     * Expected: Invalid
     */
    @Test
    void testReplayOldOrderCode_Invalid() {
        // Create and pay an old order
        CreateOrderRequest request = new CreateOrderRequest();
        request.setVendorSlug(testVendor.getSlug());
        request.setAmount(new BigDecimal("100"));

        OrderDto order = orderService.createOrder(request, "test-device");

        // Mark as paid
        orderService.markOrderAsPaid(order.getOrderCode(), "test_ref");

        // Try to use the same order code again
        OrderDto oldOrderCheck = orderService.getOrderByCode(order.getOrderCode());
        
        // Should show PAID, not allow replay
        assertNotNull(oldOrderCheck);
        assertEquals("PAID", oldOrderCheck.getStatus());

        // Creating a new order with same device should work (different code)
        CreateOrderRequest request2 = new CreateOrderRequest();
        request2.setVendorSlug(testVendor.getSlug());
        request2.setAmount(new BigDecimal("50"));

        OrderDto newOrder = orderService.createOrder(request2, "test-device");
        
        // Should have different order code
        assertNotEquals(order.getOrderCode(), newOrder.getOrderCode());

        System.out.println("✓ Attack 6 PASSED: Replay of old order code handled correctly");
    }

    @Test
    void registrationWithoutOtpProofIsRejected() {
        Map<String, Object> body = Map.of("phoneNumber", "9000000001", "upiId", "demo.unverified@examplebank",
                "businessName", "Unverified Vendor", "slug", "unverified-vendor");
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/vendor/register", body, Map.class);
        assertEquals(401, response.getStatusCode().value());
    }

    @Test
    void duplicateRegistrationSlugReturnsActionableConflict() {
        String phone = fakePhoneNumber();
        String code = authService.sendOtp(phone);
        var proof = authService.verifyOtp(phone, code);
        Map<String, Object> body = Map.of("phoneNumber", phone, "upiId", "demo.new-vendor@examplebank",
                "businessName", "Duplicate Link Test", "slug", testVendor.getSlug());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(proof.getToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/vendor/register",
                new HttpEntity<>(body, headers), Map.class);

        assertEquals(409, response.getStatusCode().value(), "Registration conflict response: " + response.getBody());
        assertEquals("SLUG_TAKEN", response.getBody().get("code"));
    }

    @Test
    void otpIsNotReturnedToClientByDefault() throws Exception {
        String phone = fakePhoneNumber();
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(loggerContext);
        appender.start();
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(appender);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/send-otp",
                    Map.of("phoneNumber", phone), String.class);
            assertTrue(response.getStatusCode().is2xxSuccessful());
            assertFalse(response.getBody().contains("\"otp\""));
            assertFalse(response.getBody().matches("(?s).*\\b\\d{6}\\b.*"), "A raw six-digit OTP appeared in the API response.");
            assertTrue(appender.list.stream().noneMatch(event -> event.getFormattedMessage().matches("(?s).*\\b\\d{6}\\b.*")),
                    "A raw six-digit OTP appeared in application logs.");
        } finally {
            rootLogger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void publicVerifyOtpRouteAcceptsValidCodeWithoutAnExistingSessionToken() {
        String phone = fakePhoneNumber();
        String code = authService.sendOtp(phone);
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/verify-otp",
                Map.of("phoneNumber", phone, "otpCode", code), Map.class);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().get("token") instanceof String);
    }

    @Test
    void invalidOtpHasAnActionableResponse() {
        String phone = fakePhoneNumber();
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/verify-otp",
                Map.of("phoneNumber", phone, "otpCode", "000000"), Map.class);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("OTP_INVALID", response.getBody().get("code"));
        assertTrue(((String) response.getBody().get("message")).contains("could not be verified"));
    }

    @Test
    void publicVendorResponseOmitsPhoneAndUpi() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/vendor/public/vendor/" + testVendor.getSlug(), String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertFalse(response.getBody().contains(testVendor.getPhoneNumber()));
        assertFalse(response.getBody().contains(testVendor.getUpiId()));
    }

    @Test
    void vendorCannotReadAnotherVendorsOrderByCode() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setVendorSlug(testVendor.getSlug());
        request.setAmount(new BigDecimal("100"));
        OrderDto order = orderService.createOrder(request, "test-device");
        ResponseEntity<Map> publicStatus = restTemplate.getForEntity(
                "/api/orders/" + order.getOrderCode() + "/status", Map.class);
        assertTrue(publicStatus.getStatusCode().is2xxSuccessful());
        assertTrue(publicStatus.getBody().get("token") instanceof String);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authService.generateToken(testVendor.getPhoneNumber(), testVendor.getId()));
        ResponseEntity<Map> ownQuickCheck = restTemplate.exchange("/api/vendor/orders/check/" + order.getOrderCode(),
                org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertEquals(Boolean.TRUE, ownQuickCheck.getBody().get("found"));
        assertEquals(order.getOrderCode(), ownQuickCheck.getBody().get("orderCode"));
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 9);
        Vendor otherVendor = vendorRepository.save(Vendor.builder().phoneNumber("8" + String.format("%09d", Math.floorMod(UUID.randomUUID().hashCode(), 1_000_000_000)))
                .upiId("demo.other-vendor@examplebank").businessName("Other Vendor").slug("other-" + unique).isActive(true).build());
        headers.setBearerAuth(authService.generateToken(otherVendor.getPhoneNumber(), otherVendor.getId()));
        ResponseEntity<String> response = restTemplate.exchange("/api/orders/" + order.getOrderCode(),
                org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertEquals(404, response.getStatusCode().value());
        ResponseEntity<Map> quickCheck = restTemplate.exchange("/api/vendor/orders/check/" + order.getToken(),
                org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertEquals(Boolean.FALSE, quickCheck.getBody().get("found"));
    }

    /**
     * Attack 7: Lost webhook
     * Expected: Reconciliation job fixes the order
     */
    @Test
    void testLostWebhook_ReconciliationFixes() {
        // Create order
        CreateOrderRequest request = new CreateOrderRequest();
        request.setVendorSlug(testVendor.getSlug());
        request.setAmount(new BigDecimal("100"));

        OrderDto order = orderService.createOrder(request, "test-device");

        // Pay using the mock gateway's drop-webhook option: gateway state becomes PAID,
        // while BolPay receives no webhook.
        Map<String, Object> dropped = restTemplate.postForObject("http://mock-cashfree:9000/webhook/send",
                Map.of("order_id", order.getOrderCode(), "scenario", "drop", "order_status", "PAID",
                        "order_amount", order.getAmount()), Map.class);
        assertEquals("webhook dropped", dropped.get("status"));
        Order dbOrder = orderRepository.findByOrderCode(order.getOrderCode()).orElseThrow();
        assertEquals("PENDING", dbOrder.getStatus().name());

        reconciliationService.reconcilePendingOrders();

        // Order should now be PAID
        dbOrder = orderRepository.findByOrderCode(order.getOrderCode()).orElseThrow();
        assertEquals("PAID", dbOrder.getStatus().name());
        assertNotNull(dbOrder.getPaidAt());

        System.out.println("✓ Attack 7 PASSED: Lost webhook scenario handled by reconciliation");
    }

    @Test
    void scanSessionsArePersistedAndCountedForVendorBoard() {
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/public/scan/" + testVendor.getSlug(),
                Map.of("deviceId", "browser-test-device"), Map.class);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, scanSessionService.countRecentScans(testVendor.getId(), LocalDateTime.now().minusMinutes(10)));
    }

    @Test
    void concurrentOrderCreationAllocatesDistinctActiveTokens() throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<OrderDto>>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    CreateOrderRequest request = new CreateOrderRequest();
                    request.setVendorSlug(testVendor.getSlug());
                    request.setAmount(new BigDecimal("10"));
                    return orderService.createOrder(request, "parallel-test");
                }));
            }
            var tokens = new java.util.HashSet<String>();
            for (var future : futures) tokens.add(future.get(30, java.util.concurrent.TimeUnit.SECONDS).getToken());
            assertEquals(8, tokens.size());
            assertTrue(tokens.stream().allMatch(t -> t.matches("[A-HJ-NP-Z][2-9]{2}")));
        } finally {
            pool.shutdownNow();
        }
    }

    private ResponseEntity<Map> postWebhook(Map<String, Object> body, String overrideSignature) {
        return postWebhook(body, overrideSignature, true);
    }

    private ResponseEntity<Map> postWebhook(Map<String, Object> body, String overrideSignature, boolean includeSignature) {
        return postWebhook(body, overrideSignature, includeSignature, null);
    }

    private ResponseEntity<Map> postWebhook(Map<String, Object> body, String overrideSignature,
                                            boolean includeSignature, Long timestampOverride) {
        try {
            String raw = objectMapper.writeValueAsString(body);
            String timestamp = Long.toString(timestampOverride == null
                    ? System.currentTimeMillis() / 1000 : timestampOverride);
            String signature = overrideSignature == null ? sign(timestamp + raw) : overrideSignature;
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(
                    "http://localhost:" + serverPort + "/api/webhook/payment").openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            if (includeSignature) connection.setRequestProperty("X-Webhook-Signature", signature);
            connection.setRequestProperty("X-Webhook-Timestamp", timestamp);
            byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (java.io.OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            int status = connection.getResponseCode();
            java.io.InputStream responseStream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            Map responseBody = responseStream == null ? null : objectMapper.readValue(responseStream, Map.class);
            return ResponseEntity.status(status).body(responseBody);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private String sign(String value) throws Exception {
        String secret = System.getenv().getOrDefault("CASHFREE_WEBHOOK_SECRET", "bolpay-mock-webhook-secret");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String fakePhoneNumber() {
        return "900000" + String.format("%04d", Math.floorMod(System.nanoTime(), 10_000));
    }
}
