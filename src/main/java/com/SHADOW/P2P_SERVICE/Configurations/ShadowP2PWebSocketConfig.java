package com.SHADOW.P2P_SERVICE.Configurations;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import lombok.extern.slf4j.Slf4j;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class ShadowP2PWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${ghost.shield.jwt-secret}")
    private String jwtSecret;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) return message;

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    List<String> authHeaders = accessor.getNativeHeader("Authorization");

                    if (authHeaders == null || authHeaders.isEmpty() || !authHeaders.get(0).startsWith("Bearer ")) {
                        log.error("STOMP connection dropped: missing or malformed Authorization header.");
                        throw new IllegalArgumentException("Unauthorized connection attempt");
                    }

                    String token = authHeaders.get(0).substring(7);

                    try {
                        Claims claims = Jwts.parser()
                                .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                                .build()
                                .parseSignedClaims(token)
                                .getPayload();

                        final String username = claims.getSubject().trim().toLowerCase();
                        log.info("Secure STOMP session established for user: {}", username);

                        accessor.setUser(new StompPrincipal(username));

                    } catch (Exception e) {
                        log.error("STOMP connection dropped: invalid JWT.");
                        throw new IllegalArgumentException("Invalid Token");
                    }
                }

                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    Principal user = accessor.getUser();
                    String destination = accessor.getDestination();

                    if (user == null) {
                        throw new IllegalArgumentException("Unauthenticated subscribe");
                    }

                    if (destination != null && destination.startsWith("/topic/shadow-")) {
                        String roomId = destination.substring("/topic/shadow-".length());
                        if (!roomContainsUser(roomId, user.getName())) {
                            log.warn("Rejected subscribe by {} to foreign room {}", user.getName(), roomId);
                            throw new IllegalArgumentException("Not a participant in this room");
                        }
                    }
                }

                return message;
            }
        });
    }

    private boolean roomContainsUser(String roomId, String username) {
        if (roomId == null || username == null || username.isEmpty()) return false;
        String room = roomId.toLowerCase();
        String user = username.toLowerCase();

        if (room.equals(user)) return true;
        if (room.startsWith(user + "_")) return true;
        if (room.endsWith("_" + user)) return true;
        return room.contains("_" + user + "_");
    }

    public static class StompPrincipal implements Principal, java.io.Serializable {
        private final String name;
        public StompPrincipal(String name) { this.name = name; }
        @Override public String getName() { return name; }
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // Increase buffer for STOMP to 1 Megabyte
        registration.setMessageSizeLimit(1024 * 1024);
        registration.setSendBufferSizeLimit(1024 * 1024);
        registration.setSendTimeLimit(20000);
    }

    // 🟢 ADDED THIS BEAN TO FIX THE TOMCAT CRASH
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        // Force Tomcat to accept 2 Megabyte raw WebSocket frames
        container.setMaxTextMessageBufferSize(2 * 1024 * 1024);
        container.setMaxBinaryMessageBufferSize(2 * 1024 * 1024);
        return container;
    }
}