package com.bolpay.service;

public class OtpExpiredException extends RuntimeException {
    public OtpExpiredException() {
        super("OTP expired");
    }
}
