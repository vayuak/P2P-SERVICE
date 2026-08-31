package com.SHADOW.P2P_SERVICE.Clients;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignInterceptorConfig {

    // 🟢 SECURE PRACTICE: Pulls safely from application.yml mapped properties
    @Value("${ghost.gateway.secret}")
    private String gatewaySecret;

    @Value("${ghost.shield.key}")
    private String shieldKey;

    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> {
            // 🟢 CORRECT HEADERS: Matches exactly what ShieldHandshakeFilter expects
            template.header("X-Gateway-Secret", gatewaySecret);
            template.header("X-Ghost-Shield-Key", shieldKey);
        };
    }
}