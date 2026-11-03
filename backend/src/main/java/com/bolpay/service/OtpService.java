package com.bolpay.service;

import com.bolpay.entity.OtpSession;
import com.bolpay.repository.OtpSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

/**
 * Service for OTP generation and verification
 * Uses mock OTP provider for demo purposes
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final OtpSessionRepository otpSessionRepository;
    private static final Random random = new SecureRandom();
    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int OTP_LENGTH = 6;

    /**
     * Generate and send OTP for the given phone number
     * In production, this would integrate with an SMS gateway
     * For demo, we log the OTP and return it
     * OTP is now hashed before storing in database
     */
    @Transactional
    public String generateAndSendOtp(String phoneNumber) {
        // Generate 6-digit OTP
        String otpCode = String.format("%0" + OTP_LENGTH + "d", random.nextInt((int) Math.pow(10, OTP_LENGTH)));

        // Hash OTP before storing
        String hashedOtp = hashOtp(otpCode);

        // Create OTP session with hashed OTP
        OtpSession session = OtpSession.builder()
                .phoneNumber(phoneNumber)
                .otpCode(hashedOtp)
                .expiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
                .build();

        otpSessionRepository.save(session);

        // In production, send via SMS gateway
        // For demo, log the OTP
        log.info("MOCK OTP for {}: {}", phoneNumber, otpCode);

        // Return OTP for demo purposes (remove in production)
        return otpCode;
    }

    /**
     * Verify OTP for the given phone number
     * Compares hash of provided OTP with stored hash
     */
    @Transactional
    public boolean verifyOtp(String phoneNumber, String otpCode) {
        List<OtpSession> validSessions = otpSessionRepository
                .findByPhoneNumberAndUsedAtIsNullAndExpiresAtAfter(phoneNumber, LocalDateTime.now());

        if (validSessions.isEmpty()) {
            log.warn("No valid OTP session found for phone: {}", phoneNumber);
            return false;
        }

        // Check the most recent session
        OtpSession session = validSessions.get(validSessions.size() - 1);

        // Hash the provided OTP and compare
        String hashedOtp = hashOtp(otpCode);
        if (!session.getOtpCode().equals(hashedOtp)) {
            log.warn("Invalid OTP for phone: {}", phoneNumber);
            return false;
        }

        // Mark as used
        session.setUsedAt(LocalDateTime.now());
        otpSessionRepository.save(session);

        log.info("OTP verified successfully for phone: {}", phoneNumber);
        return true;
    }

    /**
     * Scheduled task to clean up expired OTP sessions
     */
    @Scheduled(fixedRate = 3600000) // Run every hour
    @Transactional
    public void cleanupExpiredSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        otpSessionRepository.deleteByExpiresAtBefore(cutoff);
        log.info("Cleaned up expired OTP sessions");
    }

    /**
     * Hash OTP code using SHA-256
     */
    private String hashOtp(String otpCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(otpCode.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
