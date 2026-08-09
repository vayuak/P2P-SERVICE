package com.SHADOW.P2P_SERVICE.Listeners;

import com.SHADOW.P2P_SERVICE.Controllers.P2PChatController;
import com.SHADOW.P2P_SERVICE.Models.OfflineMessage;
import com.SHADOW.P2P_SERVICE.Repositories.OfflineMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketMailboxFlusher {

    private final OfflineMessageRepository offlineMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    @EventListener
    public void handleSessionSubscribeEvent(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = headerAccessor.getDestination();

        if (headerAccessor.getUser() == null || destination == null) return;
        String username = headerAccessor.getUser().getName();

        // Trigger when they subscribe to a specific chat room
        if (destination.startsWith("/topic/shadow-")) {
            List<OfflineMessage> pendingMessages = offlineMessageRepository.findByRecipientUsernameOrderByTimestampAsc(username);

            if (!pendingMessages.isEmpty()) {
                for (OfflineMessage msg : pendingMessages) {
                    // Re-build the payload exactly how the frontend expects it
                    P2PChatController.FortressPayload payload = new P2PChatController.FortressPayload();
                    payload.setRoomId(msg.getRoomId());
                    payload.setCiphertext(msg.getCiphertext());
                    payload.setIv(msg.getIv());
                    payload.setAuthTag(msg.getAuthTag());
                    // ... set other fields

                    // Blast it directly into the room they just joined
                    messagingTemplate.convertAndSend(destination, payload);
                }
                offlineMessageRepository.deleteByRecipientUsername(username);
            }
        }
    }
}