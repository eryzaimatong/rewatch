package com.rewatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RewatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(RewatchApplication.class, args);
    }
}