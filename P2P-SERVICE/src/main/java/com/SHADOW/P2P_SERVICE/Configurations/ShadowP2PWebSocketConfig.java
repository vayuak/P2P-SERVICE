package com.SHADOW.P2P_SERVICE.Configurations;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class ShadowP2PWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${ghost.shield.jwt-secret:SuperSecurePermanentSecretKeyThatIsAtLeast64BytesLongForSecurityGuarantees}")
    private String jwtSecret;

    @Value("${GATEWAY_SECRET:CryptographicGhostShieldInternalTokenSignature7350_465}")
    private String gatewaySecret;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {

                        if (request instanceof ServletServerHttpRequest) {
                            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
                            String incomingSecret = servletRequest.getHeader("X-Gateway-Secret");

                            if (incomingSecret == null || !incomingSecret.equals(gatewaySecret)) {
                                log.warn("🚨 SECURITY BREACH ATTEMPT: WebSocket connection rejected. Invalid Gateway Secret.");
                                response.setStatusCode(HttpStatus.FORBIDDEN);
                                return false;
                            }
                        }
                        return true;
                    }

                    @Override
                    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                               WebSocketHandler wsHandler, Exception exception) {}
                });
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 🟢 FIXED: Using 'registration' instead of 'registry'
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    List<String> authHeaders = accessor.getNativeHeader("Authorization");

                    if (authHeaders == null || authHeaders.isEmpty() || !authHeaders.get(0).startsWith("Bearer ")) {
                        log.error("🚨 STOMP Connection Dropped: Missing or malformed Authorization header.");
                        throw new IllegalArgumentException("Unauthorized connection attempt");
                    }

                    String token = authHeaders.get(0).substring(7);

                    try {
                        Claims claims = Jwts.parser()
                                .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                                .build()
                                .parseSignedClaims(token)
                                .getPayload();

                        log.info("✅ Secure STOMP session established for user: {}", claims.getSubject());
                    } catch (Exception e) {
                        log.error("🚨 STOMP Connection Dropped: Invalid JWT Signature.");
                        throw new IllegalArgumentException("Invalid Token");
                    }
                }
                return message;
            }
        });
    }
}