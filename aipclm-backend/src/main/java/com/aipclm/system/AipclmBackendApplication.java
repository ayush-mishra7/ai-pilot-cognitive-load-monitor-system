package com.aipclm.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

// Excluded SecurityAutoConfiguration to ensure authentication does not block any endpoints for now
@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
public class AipclmBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(AipclmBackendApplication.class, args);
    }
}
