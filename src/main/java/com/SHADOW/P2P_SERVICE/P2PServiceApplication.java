package com.SHADOW.P2P_SERVICE;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
public class P2PServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(P2PServiceApplication.class, args);
    }
}