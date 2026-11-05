package com.bolpay.controller;

import com.bolpay.dto.AuthResponse;
import com.bolpay.dto.SendOtpRequest;
import com.bolpay.dto.VerifyOtpRequest;
import com.bolpay.service.AuthService;
import com.bolpay.service.OtpAttemptLimitException;
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

    @Value("${otp.return-to-client:false}")
    private boolean returnOtpToClient;

    /**
     * Send OTP to phone number
     * POST /api/auth/send-otp
     */
    @PostMapping("/send-otp")
    public ResponseEntity<Map<String, String>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        String otp = authService.sendOtp(request.getPhoneNumber());
        Map<String, String> response = returnOtpToClient
                ? Map.of("message", "OTP sent successfully", "otp", otp)
                : Map.of("message", "OTP sent successfully");
        return ResponseEntity.ok(response);
    }

    /**
     * Verify OTP and return JWT token
     * POST /api/auth/verify-otp
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        try {
            AuthResponse authResponse = authService.verifyOtp(request.getPhoneNumber(), request.getOtpCode());
            return ResponseEntity.ok(authResponse);
        } catch (OtpAttemptLimitException ex) {
            return ResponseEntity.status(429).build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }
}
