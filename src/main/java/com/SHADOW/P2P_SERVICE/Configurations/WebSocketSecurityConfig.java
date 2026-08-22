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

/**
 * Per-user WebSocket connection limiter.
 *
 * WHAT WAS BROKEN BEFORE
 * ----------------------
 * The previous version was a ChannelInterceptor on the inbound channel that:
 *
 *   1. Read the connection owner from a custom native header "userId". The
 *      client never sent that header, so it was always null and the per-user
 *      limit was skipped entirely.
 *   2. Read the client IP from "X-Forwarded-For", which is an HTTP header and
 *      is not present on a STOMP frame. It therefore fell through to the
 *      literal string "UNKNOWN_IP", putting EVERY user on the platform into
 *      one shared bucket capped at 50.
 *   3. Decremented only on an explicit STOMP DISCONNECT frame, read from the
 *      same absent headers. Mobile clients that background, lose signal, or
 *      are killed never send DISCONNECT, so the counter only ever went up.
 *
 * Net effect: after 50 cumulative connections since the last deploy, across
 * all users for all time, every new connection was rejected with
 * "IP rate limit exceeded." until the service restarted.
 *
 * WHAT CHANGED
 * ------------
 * Counting now happens on SessionConnectedEvent / SessionDisconnectEvent.
 * Spring fires the disconnect event for ANY session teardown, including
 * abrupt socket loss, so the counter is self-healing. Identity comes from the
 * authenticated Principal that ShadowP2PWebSocketConfig already sets from the
 * verified JWT, which cannot be spoofed by a client header.
 *
 * The IP bucket is gone. It could not work: the STOMP layer has no access to
 * the originating IP, and the placeholder value made it actively harmful.
 * Enforce IP limits at the ingress/proxy layer instead, where the real client
 * address is available.
 *
 * Note this is per-instance state. That is fine while you run a single
 * replica, which you must for the in-memory simple broker anyway.
 */
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
