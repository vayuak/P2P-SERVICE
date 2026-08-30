package com.SHADOW.P2P_SERVICE;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableFeignClients(basePackages = "com.SHADOW.P2P_SERVICE.Clients")
public class P2PServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(P2PServiceApplication.class, args);
    }
}
