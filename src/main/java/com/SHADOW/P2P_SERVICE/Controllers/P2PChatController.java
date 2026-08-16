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
    private final SimpUserRegistry userRegistry;
    private final OfflineMessageRepository offlineRepo;

    @MessageMapping("/shadow/send")
    public void relayEncryptedMessage(@Payload FortressPayload payload) {
        log.info("🛡️ [ZERO-TRUST RELAY] Routing AEAD packet for Room: {}", payload.getRoomId());

        String destination = "/topic/shadow-" + payload.getRoomId().toLowerCase().trim();

        String senderUsername = payload.getSenderUsername();
        String targetUsername = extractTargetUsername(payload.getRoomId(), senderUsername);

        boolean isTargetOnline = userRegistry.getUser(targetUsername) != null;

        if (isTargetOnline) {
            messagingTemplate.convertAndSend(destination, payload);
        } else {
            log.info("💤 User {} is offline. Saving to encrypted mailbox.", targetUsername);

            OfflineMessage offlineMsg = new OfflineMessage();
            offlineMsg.setSenderUsername(senderUsername);
            offlineMsg.setRecipientUsername(targetUsername);
            offlineMsg.setRoomId(payload.getRoomId());

            // 🟢 THE FIX: We must assign the text to BOTH fields just in case,
            // but specifically 'EncryptedPayload' to satisfy your PostgreSQL NOT NULL constraint!
            offlineMsg.setCiphertext(payload.getCiphertext());
            offlineMsg.setEncryptedPayload(payload.getCiphertext());

            offlineMsg.setIv(payload.getIv());
            offlineMsg.setAuthTag(payload.getAuthTag());
            offlineMsg.setEphemeralPublicKey(payload.getEphemeralPublicKey());

            offlineRepo.save(offlineMsg);
        }
    }

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
        private String ephemeralPublicKey;
        private String ciphertext;
        private String iv;
        private String authTag;
    }
}