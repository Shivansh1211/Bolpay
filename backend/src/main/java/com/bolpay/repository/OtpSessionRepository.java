package com.bolpay.repository;

import com.bolpay.entity.OtpSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for OTP session entity
 */
@Repository
public interface OtpSessionRepository extends JpaRepository<OtpSession, Long> {

    Optional<OtpSession> findFirstByPhoneNumberAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDescIdDesc(
            String phoneNumber, LocalDateTime now);

    Optional<OtpSession> findFirstByPhoneNumberAndUsedAtIsNullOrderByCreatedAtDescIdDesc(String phoneNumber);

    List<OtpSession> findByPhoneNumberAndUsedAtIsNull(String phoneNumber);

    void deleteByExpiresAtBefore(LocalDateTime now);
}
