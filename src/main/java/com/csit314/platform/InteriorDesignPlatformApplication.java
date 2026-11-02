package com.csit314.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// IDR-08 needs @EnableScheduling for the weekly digest job
@SpringBootApplication
@EnableScheduling
public class InteriorDesignPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(InteriorDesignPlatformApplication.class, args);
    }

}
