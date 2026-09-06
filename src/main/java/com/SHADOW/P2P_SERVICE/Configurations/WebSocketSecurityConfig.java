package com.SHADOW.P2P_SERVICE.Configurations;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
@Component
@Slf4j
public class WebSocketSecurityConfig {

    private static final int MAX_SESSIONS_PER_USER = 5;

    private final Map<String, Integer> sessionsPerUser = new ConcurrentHashMap<>();
    // sessionId -> username, so disconnect can decrement the right bucket even
    // though the DISCONNECT frame carries no user information.
    private final Map<String, String> sessionOwner = new ConcurrentHashMap<>();

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        Principal principal = accessor.getUser();

        if (sessionId == null || principal == null) return;

        String username = principal.getName();
        sessionOwner.put(sessionId, username);

        int count = sessionsPerUser.merge(username, 1, Integer::sum);
        log.debug("Session {} opened for {} ({} active)", sessionId, username, count);

        if (count > MAX_SESSIONS_PER_USER) {
            // Log only. Do not throw: the session is already established and
            // throwing here would leave the counter inconsistent. If you want
            // hard enforcement, close the oldest session for this user instead
            // of rejecting the newest, so a client with a stale socket can
            // still recover.
            log.warn("User {} has {} concurrent sessions (soft limit {}).",
                    username, count, MAX_SESSIONS_PER_USER);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        if (sessionId == null) return;

        String username = sessionOwner.remove(sessionId);
        if (username == null) return;

        sessionsPerUser.compute(username, (key, current) -> {
            if (current == null || current <= 1) return null; // drop the entry, no leak
            return current - 1;
        });

        log.debug("Session {} closed for {}", sessionId, username);
    }
}
