package com.bolpay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main application class for BolPay - Payment verification platform
 * for small Indian vendors.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
@EnableAsync
public class BolPayApplication {

    public static void main(String[] args) {
        DatabaseUrlValidator.validate(System.getenv("SPRING_DATASOURCE_URL"));
        SpringApplication.run(BolPayApplication.class, args);
    }
}
