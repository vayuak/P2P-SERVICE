package com.SHADOW.P2P_SERVICE.Controllers;

import com.SHADOW.P2P_SERVICE.Models.OfflineMessage;
import com.SHADOW.P2P_SERVICE.Repositories.OfflineMessageRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@Slf4j
@RequiredArgsConstructor
public class P2PChatController {

    private final SimpMessagingTemplate messagingTemplate;
    // Removed SimpUserRegistry because it fails to track custom STOMP JWT Principals
    private final OfflineMessageRepository offlineRepo;

    @MessageMapping("/shadow/send")
    public void relayEncryptedMessage(@Payload FortressPayload payload, Principal principal) {
        if (principal == null) {
            log.warn("Rejected unauthenticated relay attempt.");
            return;
        }

        String senderUsername = principal.getName().trim().toLowerCase();

        if (payload.getRoomId() == null || payload.getRoomId().isBlank()) {
            log.warn("Rejected relay with no roomId from {}", senderUsername);
            return;
        }

        String roomId = payload.getRoomId().trim().toLowerCase();

        String targetUsername = resolveTarget(payload, roomId, senderUsername);
        if (targetUsername == null) {
            log.warn("Could not resolve recipient for room {} (sender {}). Dropping.", roomId, senderUsername);
            return;
        }

        if (!roomContainsUser(roomId, senderUsername)) {
            log.warn("Rejected relay: {} is not a participant in room {}", senderUsername, roomId);
            return;
        }

        payload.setRoomId(roomId);
        payload.setSenderUsername(senderUsername);
        payload.setTargetUsername(targetUsername);

        /*
         * 1. ALWAYS VAULT (Store)
         * We save the message to PostgreSQL immediately. If the user is offline,
         * it waits for them to call /v1/p2p/sync.
         */
        log.info("Vaulting message from {} to {}", senderUsername, targetUsername);
        OfflineMessage offlineMsg = new OfflineMessage();
        offlineMsg.setSenderUsername(senderUsername);
        offlineMsg.setRecipientUsername(targetUsername);
        offlineMsg.setRoomId(roomId);
        offlineMsg.setMsgId(payload.getMsgId());
        offlineMsg.setEncryptedPayload(payload.getCiphertext());
        offlineMsg.setIv(payload.getIv());
        offlineMsg.setAuthTag(payload.getAuthTag());
        offlineMsg.setEphemeralPublicKey(payload.getEphemeralPublicKey());
        offlineRepo.save(offlineMsg);

        /*
         * 2. ALWAYS RELAY (Forward)
         * We blast the message down the target user's personal WebSocket inbox.
         * If they are online, their GlobalNetworkManager receives it instantly.
         * If they are offline, the STOMP broker safely ignores it.
         */
        String destination = "/topic/shadow-user-" + targetUsername;
        log.info("Relaying live AEAD packet to personal inbox: {}", destination);
        messagingTemplate.convertAndSend(destination, payload);
    }

    private String resolveTarget(FortressPayload payload, String roomId, String senderUsername) {
        String supplied = payload.getTargetUsername();
        if (supplied != null && !supplied.isBlank()) {
            String target = supplied.trim().toLowerCase();
            if (!target.equals(senderUsername) && roomContainsUser(roomId, target)) {
                return target;
            }
        }

        // Fallback: remove the sender's run from the room id.
        if (roomId.startsWith(senderUsername + "_")) {
            String rest = roomId.substring(senderUsername.length() + 1);
            return rest.isBlank() ? null : rest;
        }
        if (roomId.endsWith("_" + senderUsername)) {
            String rest = roomId.substring(0, roomId.length() - senderUsername.length() - 1);
            return rest.isBlank() ? null : rest;
        }
        return null;
    }

    private boolean roomContainsUser(String roomId, String username) {
        if (roomId == null || username == null || username.isEmpty()) return false;
        if (roomId.equals(username)) return true;
        if (roomId.startsWith(username + "_")) return true;
        if (roomId.endsWith("_" + username)) return true;
        return roomId.contains("_" + username + "_");
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FortressPayload {
        private String roomId;
        private Long senderId;
        private String msgId;
        private String senderUsername;
        private String targetUsername;
        private String ephemeralPublicKey;
        private String ciphertext;
        private String iv;
        private String authTag;
    }
}