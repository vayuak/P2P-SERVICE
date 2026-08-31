package com.SHADOW.P2P_SERVICE.Configurations;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ShieldHandshakeFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ShieldHandshakeFilter.class);

    // 🟢 SECURE PRACTICE: Pulling from environment, no hardcoded fallbacks
    @Value("${ghost.shield.key}")
    private String expectedClientKey;

    @Value("${ghost.gateway.secret}")
    private String expectedGatewaySecret;

    private static final String SHIELD_KEY_HEADER = "X-Ghost-Shield-Key";
    private static final String GATEWAY_SECRET_HEADER = "X-Gateway-Secret";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Let the WebSocket Handshake pass through! Security happens at the STOMP level.
        if (request.getRequestURI().startsWith("/ws-chat")) {
            filterChain.doFilter(request, response);
            return;
        }

        String incomingShieldKey = request.getHeader(SHIELD_KEY_HEADER);
        String incomingGatewaySecret = request.getHeader(GATEWAY_SECRET_HEADER);

        if (incomingShieldKey != null) incomingShieldKey = incomingShieldKey.trim();
        if (incomingGatewaySecret != null) incomingGatewaySecret = incomingGatewaySecret.trim();

        boolean isClientKeyValid = incomingShieldKey != null && incomingShieldKey.contains(expectedClientKey);
        boolean isGatewaySecretValid = incomingGatewaySecret != null && incomingGatewaySecret.contains(expectedGatewaySecret);

        if (isClientKeyValid && isGatewaySecretValid) {
            filterChain.doFilter(request, response);
        } else {
            log.error("INTRUDER REJECTED: Malicious direct connection attempt aborted from IP: {}", request.getRemoteAddr());
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"GHOST_SHIELD_VIOLATION\", \"message\": \"Direct microservice access is denied.\"}");
        }
    }
}