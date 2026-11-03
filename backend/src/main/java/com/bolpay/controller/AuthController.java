package com.bolpay.controller;

import com.bolpay.dto.AuthResponse;
import com.bolpay.dto.SendOtpRequest;
import com.bolpay.dto.VerifyOtpRequest;
import com.bolpay.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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

    /**
     * Send OTP to phone number
     * POST /api/auth/send-otp
     */
    @PostMapping("/send-otp")
    public ResponseEntity<Map<String, String>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        String otp = authService.sendOtp(request.getPhoneNumber());
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "OTP sent successfully");
        response.put("otp", otp); // Only for demo - remove in production
        
        return ResponseEntity.ok(response);
    }

    /**
     * Verify OTP and return JWT token
     * POST /api/auth/verify-otp
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        AuthResponse authResponse = authService.verifyOtp(request.getPhoneNumber(), request.getOtpCode());
        return ResponseEntity.ok(authResponse);
    }
}
