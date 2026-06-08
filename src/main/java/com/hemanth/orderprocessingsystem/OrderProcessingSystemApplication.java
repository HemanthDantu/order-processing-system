package com.hemanth.orderprocessingsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point.
 *
 * <p>Scheduling is enabled here so the pending-order processor can run in the
 * background. The same processing service is also exposed through an admin-only
 * manual trigger for review and operational testing.</p>
 */
@EnableScheduling
@SpringBootApplication
public class OrderProcessingSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderProcessingSystemApplication.class, args);
    }

}
