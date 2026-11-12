package com.bolpay.tests;

import com.bolpay.entity.OtpSession;
import com.bolpay.repository.OtpSessionRepository;
import com.bolpay.service.OtpExpiredException;
import com.bolpay.service.OtpService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OtpServiceTest {

    @Test
    void persistedOtpCanBeVerifiedByFreshServiceInstance() {
        OtpSessionRepository repository = mock(OtpSessionRepository.class);
        AtomicReference<OtpSession> persisted = new AtomicReference<>();
        when(repository.findByPhoneNumberAndUsedAtIsNull(anyString())).thenReturn(List.of());
        when(repository.save(any(OtpSession.class))).thenAnswer(invocation -> {
            OtpSession session = invocation.getArgument(0);
            persisted.set(session);
            return session;
        });
        when(repository.findFirstByPhoneNumberAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDescIdDesc(
                anyString(), any(LocalDateTime.class)))
                .thenAnswer(invocation -> Optional.ofNullable(persisted.get()));

        OtpService issuer = new OtpService(repository);
        String code = issuer.generateAndSendOtp("9000001234");

        OtpService restartedInstance = new OtpService(repository);
        assertTrue(restartedInstance.verifyOtp("9000001234", code));
        assertNotNull(persisted.get().getUsedAt());
        assertEquals(64, persisted.get().getOtpCode().length());
        verify(repository).findFirstByPhoneNumberAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDescIdDesc(
                eq("9000001234"), any(LocalDateTime.class));
    }

    @Test
    void expiredOtpIsDistinguishedFromWrongOtp() {
        OtpSessionRepository repository = mock(OtpSessionRepository.class);
        OtpSession expired = OtpSession.builder()
                .phoneNumber("9000001235")
                .otpCode("0".repeat(64))
                .expiresAt(LocalDateTime.now().minusSeconds(1))
                .build();
        when(repository.findFirstByPhoneNumberAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDescIdDesc(
                anyString(), any(LocalDateTime.class))).thenReturn(Optional.empty());
        when(repository.findFirstByPhoneNumberAndUsedAtIsNullOrderByCreatedAtDescIdDesc("9000001235"))
                .thenReturn(Optional.of(expired));

        OtpExpiredException error = assertThrows(OtpExpiredException.class,
                () -> new OtpService(repository).verifyOtp("9000001235", "123456"));
        assertEquals("OTP expired", error.getMessage());
    }

    @Test
    void requestingNewOtpInvalidatesOlderUnusedSessions() {
        OtpSessionRepository repository = mock(OtpSessionRepository.class);
        OtpSession previous = OtpSession.builder()
                .phoneNumber("9000001236")
                .otpCode("0".repeat(64))
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        when(repository.findByPhoneNumberAndUsedAtIsNull("9000001236")).thenReturn(List.of(previous));
        when(repository.save(any(OtpSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        new OtpService(repository).generateAndSendOtp("9000001236");

        assertNotNull(previous.getUsedAt());
        verify(repository).saveAll(List.of(previous));
    }
}
