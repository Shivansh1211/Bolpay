package com.bolpay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for BolPay - Payment verification platform
 * for small Indian vendors.
 */
@SpringBootApplication
@EnableScheduling
public class BolPayApplication {

    public static void main(String[] args) {
        SpringApplication.run(BolPayApplication.class, args);
    }
}
