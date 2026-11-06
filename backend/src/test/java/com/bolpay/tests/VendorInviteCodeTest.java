package com.bolpay.tests;

import com.bolpay.dto.AuthResponse;
import com.bolpay.service.AuthService;
import com.bolpay.service.OtpService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "registration.invite-code=fixture-only-invite-code"
})
@ActiveProfiles("test")
class VendorInviteCodeTest {

    private static final String FIXTURE_INVITE_CODE = "fixture-only-invite-code";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AuthService authService;

    @Autowired
    private OtpService otpService;

    @LocalServerPort
    private int port;

    @Test
    void configuredInviteCodeRejectsMissingCodeWithClearMessage() {
        ResponseEntity<String> response = attemptRegistration(null);

        assertEquals(403, response.getStatusCode().value());
        assertNotNull(response.getBody(), "Missing invite code should return a clear response message.");
        assertTrue(response.getBody().contains("Enter the correct invite code"));
    }

    @Test
    void configuredInviteCodeRejectsIncorrectCodeWithClearMessage() {
        ResponseEntity<String> response = attemptRegistration("wrong-fixture-value");

        assertEquals(403, response.getStatusCode().value());
        assertNotNull(response.getBody(), "Incorrect invite code should return a clear response message.");
        assertTrue(response.getBody().contains("Enter the correct invite code"));
    }

    @Test
    void configuredInviteCodeAllowsRegistrationWithCorrectCode() {
        ResponseEntity<String> response = attemptRegistration(FIXTURE_INVITE_CODE);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("\"businessName\":\"Invite Code Test Stall\""));
    }

    private ResponseEntity<String> attemptRegistration(String inviteCode) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String phone = "900000" + String.format("%04d", Math.floorMod(System.nanoTime(), 10_000));
        String otp = otpService.generateAndSendOtp(phone);
        AuthResponse proof = authService.verifyOtp(phone, otp);

        Map<String, String> profile = new HashMap<>();
        profile.put("phoneNumber", phone);
        profile.put("upiId", "demo.invite." + suffix + "@examplebank");
        profile.put("businessName", "Invite Code Test Stall");
        profile.put("slug", "invite-" + suffix);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(proof.getToken());
        if (inviteCode != null) headers.set("X-Invite-Code", inviteCode);
        return restTemplate.exchange("http://localhost:" + port + "/api/vendor/register", HttpMethod.POST,
                new HttpEntity<>(profile, headers), String.class);
    }
}
