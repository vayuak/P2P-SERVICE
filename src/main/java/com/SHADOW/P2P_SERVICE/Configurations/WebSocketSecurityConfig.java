package com.SHADOW.P2P_SERVICE.Configurations;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class WebSocketSecurityConfig implements ChannelInterceptor {

    private final Map<String, AtomicInteger> userSocketCount = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> ipSocketCount = new ConcurrentHashMap<>();

    private static final int MAX_SOCKETS_PER_USER = 5;
    private static final int MAX_SOCKETS_PER_IP = 50;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String userId = accessor.getFirstNativeHeader("userId");
            String clientIp = accessor.getFirstNativeHeader("X-Forwarded-For");
            if (clientIp == null) clientIp = "UNKNOWN_IP";

            if (userId != null) {
                userSocketCount.putIfAbsent(userId, new AtomicInteger(0));
                if (userSocketCount.get(userId).incrementAndGet() > MAX_SOCKETS_PER_USER) {
                    userSocketCount.get(userId).decrementAndGet();
                    throw new IllegalArgumentException("Max socket limit reached.");
                }
            }

            ipSocketCount.putIfAbsent(clientIp, new AtomicInteger(0));
            if (ipSocketCount.get(clientIp).incrementAndGet() > MAX_SOCKETS_PER_IP) {
                ipSocketCount.get(clientIp).decrementAndGet();
                throw new IllegalArgumentException("IP rate limit exceeded.");
            }
        }

        if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            String userId = accessor.getFirstNativeHeader("userId");
            String clientIp = accessor.getFirstNativeHeader("X-Forwarded-For");

            if (userId != null && userSocketCount.containsKey(userId)) {
                userSocketCount.get(userId).decrementAndGet();
            }
            if (clientIp != null && ipSocketCount.containsKey(clientIp)) {
                ipSocketCount.get(clientIp).decrementAndGet();
            }
        }
        return message;
    }
}