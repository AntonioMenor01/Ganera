package com.ganera.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GaneraApplication {
    public static void main(String[] args) {
        SpringApplication.run(GaneraApplication.class, args);
    }
}
