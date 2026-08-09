package com.SHADOW.P2P_SERVICE.Controllers;

import com.SHADOW.P2P_SERVICE.Models.OfflineMessage;
import com.SHADOW.P2P_SERVICE.Repositories.OfflineMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.util.List;

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
            // 🟢 FIX: Extract the roomId from the destination!
            String roomId = destination.replace("/topic/shadow-", "");

            // 🟢 FIX: Only fetch messages for this specific room
            List<OfflineMessage> pendingMessages = offlineMessageRepository
                    .findByRecipientUsernameAndRoomIdOrderByTimestampAsc(username, roomId);

            if (!pendingMessages.isEmpty()) {
                for (OfflineMessage msg : pendingMessages) {
                    // Re-build the payload
                    P2PChatController.FortressPayload payload = new P2PChatController.FortressPayload();
                    payload.setMsgId("offline_" + msg.getId()); // 🟢 Important for frontend deduping
                    payload.setRoomId(msg.getRoomId());
                    payload.setSenderUsername(msg.getSenderUsername()); // 🟢 FIX: Added sender
                    payload.setCiphertext(msg.getCiphertext());
                    payload.setIv(msg.getIv());
                    payload.setAuthTag(msg.getAuthTag());
                    payload.setEphemeralPublicKey(msg.getEphemeralPublicKey());

                    // Blast it directly into the room they just joined
                    messagingTemplate.convertAndSend(destination, payload);
                }

                // 🟢 FIX: Only delete the messages we just delivered!
                offlineMessageRepository.deleteAll(pendingMessages);
                log.info("Flushed {} offline messages for {} in room {}", pendingMessages.size(), username, roomId);
            }
        }
    }
}