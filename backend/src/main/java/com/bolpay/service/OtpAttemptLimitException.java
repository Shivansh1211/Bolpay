package com.bolpay.service;

public class OtpAttemptLimitException extends RuntimeException {
    public OtpAttemptLimitException(String message) {
        super(message);
    }
}
