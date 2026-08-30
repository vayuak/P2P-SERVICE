package com.SHADOW.P2P_SERVICE.Clients;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignInterceptorConfig {

    @Value("${GATEWAY_SECRET}")
    private String gatewaySecret;

    @Bean
    public RequestInterceptor requestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                // Adjust "Gateway-Secret" if your User Catalog expects a different exact header name
                template.header("Gateway-Secret", gatewaySecret);
            }
        };
    }
}