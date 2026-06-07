package com.hemanth.orderprocessingsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class OrderProcessingSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderProcessingSystemApplication.class, args);
    }

}
