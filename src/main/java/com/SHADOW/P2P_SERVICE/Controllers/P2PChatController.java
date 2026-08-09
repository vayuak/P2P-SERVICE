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
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Controller;

@Controller
@Slf4j
@RequiredArgsConstructor
public class P2PChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry userRegistry; // Tracks who is currently connected
    private final OfflineMessageRepository offlineRepo;

    @MessageMapping("/shadow/send")
    public void relayEncryptedMessage(@Payload FortressPayload payload) {
        log.info("🛡️ [ZERO-TRUST RELAY] Routing AEAD packet for Room: {}", payload.getRoomId());

        String destination = "/topic/shadow-" + payload.getRoomId().toLowerCase().trim();

        // 1. Figure out who the message is meant for based on the roomId (e.g., "adi_mark")
        String senderUsername = payload.getSenderUsername();
        String targetUsername = extractTargetUsername(payload.getRoomId(), senderUsername);

        // 2. Check if the target user is currently connected to the WebSocket
        boolean isTargetOnline = userRegistry.getUser(targetUsername) != null;

        if (isTargetOnline) {
            // 🟢 Target is online. Broadcast instantly!
            messagingTemplate.convertAndSend(destination, payload);
        } else {
            // 🔴 Target is offline. Store in the 70-hour database!
            log.info("💤 User {} is offline. Saving to encrypted mailbox.", targetUsername);

            OfflineMessage offlineMsg = new OfflineMessage();
            offlineMsg.setSenderUsername(senderUsername);
            offlineMsg.setRecipientUsername(targetUsername);
            offlineMsg.setRoomId(payload.getRoomId());
            offlineMsg.setCiphertext(payload.getCiphertext());
            offlineMsg.setIv(payload.getIv());
            offlineMsg.setAuthTag(payload.getAuthTag());
            offlineMsg.setEphemeralPublicKey(payload.getEphemeralPublicKey());

            offlineRepo.save(offlineMsg);
        }
    }

    // Helper to extract the other person's name from "adi_mark"
    private String extractTargetUsername(String roomId, String sender) {
        String[] parts = roomId.split("_");
        if (parts[0].equals(sender)) return parts[1];
        if (parts.length > 1 && parts[1].equals(sender)) return parts[0];
        return "UNKNOWN";
    }
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FortressPayload {
        private String roomId;
        private Long senderId;
        private String msgId;
        private String senderUsername;
        // 🔒 Cryptographic Elements (Server is blind to this)
        private String ephemeralPublicKey; // For Perfect Forward Secrecy (ECDH)
        private String ciphertext;         // The AES-GCM encrypted message + sequence + mediaUrl
        private String iv;                 // Initialization Vector
        private String authTag;            // AEAD Integrity verification tag
    }

}