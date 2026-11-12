package com.bolpay.controller;

import com.bolpay.dto.AuthResponse;
import com.bolpay.dto.SendOtpRequest;
import com.bolpay.dto.VerifyOtpRequest;
import com.bolpay.service.AuthService;
import com.bolpay.service.OtpAttemptLimitException;
import com.bolpay.service.OtpExpiredException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;

/**
 * Controller for authentication endpoints
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    @Value("${otp.demo-enabled:false}")
    private boolean demoOtpEnabled;

    @Value("${registration.invite-code:}")
    private String registrationInviteCode;

    /**
     * Send OTP to phone number
     * POST /api/auth/send-otp
     */
    @PostMapping("/send-otp")
    public ResponseEntity<Map<String, String>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        if (demoOtpEnabled && (registrationInviteCode == null || registrationInviteCode.isBlank())) {
            throw new IllegalStateException("DEMO_OTP requires INVITE_CODE to be configured");
        }
        String otp = authService.sendOtp(request.getPhoneNumber());
        Map<String, String> response = demoOtpEnabled
                ? Map.of("message", "OTP sent successfully", "otp", otp, "mode", "demo login")
                : Map.of("message", "OTP sent successfully");
        return ResponseEntity.ok(response);
    }

    /**
     * Verify OTP and return JWT token
     * POST /api/auth/verify-otp
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        try {
            AuthResponse authResponse = authService.verifyOtp(request.getPhoneNumber(), request.getOtpCode());
            return ResponseEntity.ok(authResponse);
        } catch (OtpAttemptLimitException ex) {
            return ResponseEntity.status(429).body(Map.of(
                    "code", "OTP_RATE_LIMITED",
                    "message", "Too many incorrect codes. Request a new code before trying again."));
        } catch (OtpExpiredException ex) {
            return ResponseEntity.status(410).body(Map.of(
                    "code", "OTP_EXPIRED",
                    "message", "This code expired. Request a new code."));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "OTP_INVALID",
                    "message", "That code could not be verified. Check it and try again."));
        }
    }
}
