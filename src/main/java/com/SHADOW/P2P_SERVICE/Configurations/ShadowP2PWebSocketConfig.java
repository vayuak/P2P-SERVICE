package com.SHADOW.P2P_SERVICE.Configurations;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
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

import lombok.extern.slf4j.Slf4j;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class ShadowP2PWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * SECURITY: no default value.
     *
     * The previous version fell back to a hardcoded key that is committed to a
     * public repository. If JWT_SECRET was ever unset, the service silently
     * accepted tokens signed with a publicly known secret, letting anyone forge
     * a session as any user. Now the application refuses to start instead.
     *
     * Rotate this secret. The old one must be treated as compromised.
     */
    @Value("${ghost.shield.jwt-secret}")
    private String jwtSecret;

    // The connection limiter is now an event listener, not a channel
    // interceptor, so it is no longer registered here.

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // TODO: replace "*" with your actual app origins before any web build.
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic");
        registry.setApplicationDestinationPrefixes("/app");
        // Required for convertAndSendToUser and /user/** destinations.
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

                        // Serializable principal so it survives into the
                        // session-connected / disconnect events.
                        accessor.setUser(new StompPrincipal(username));

                    } catch (Exception e) {
                        log.error("STOMP connection dropped: invalid JWT.");
                        throw new IllegalArgumentException("Invalid Token");
                    }
                }

                // SUBSCRIBE authorization: a user may only subscribe to a room
                // they are a participant in. Without this, any authenticated
                // user could subscribe to /topic/shadow-* for any conversation
                // and stream its ciphertext plus full metadata.
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

    /**
     * Room ids are the two participants' lowercased usernames sorted and joined
     * with '_'. Because a username may itself contain '_', we cannot simply
     * split. Instead we check that the room id starts with, ends with, or
     * contains the username as a whole underscore-delimited run.
     */
    private boolean roomContainsUser(String roomId, String username) {
        if (roomId == null || username == null || username.isEmpty()) return false;
        String room = roomId.toLowerCase();
        String user = username.toLowerCase();

        if (room.equals(user)) return true;
        if (room.startsWith(user + "_")) return true;
        if (room.endsWith("_" + user)) return true;
        return room.contains("_" + user + "_");
    }

    /** Minimal serializable Principal. A lambda is not serializable. */
    public static class StompPrincipal implements Principal, java.io.Serializable {
        private final String name;
        public StompPrincipal(String name) { this.name = name; }
        @Override public String getName() { return name; }
    }
}
