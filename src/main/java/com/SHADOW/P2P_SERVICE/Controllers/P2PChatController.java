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
        if (payload.getCiphertext() == null || payload.getCiphertext().isBlank()) {
            log.warn("Rejected relay with empty ciphertext from {}", senderUsername);
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
        if (payload.getV() == null) payload.setV(2);
        if (payload.getSentAt() == null || payload.getSentAt().isBlank()) {
            payload.setSentAt(java.time.Instant.now().toString());
        }

        OfflineMessage vaulted = new OfflineMessage();
        vaulted.setSenderUsername(senderUsername);
        vaulted.setRecipientUsername(targetUsername);
        vaulted.setRoomId(roomId);
        vaulted.setMsgId(payload.getMsgId());
        vaulted.setEncryptedPayload(payload.getCiphertext());
        vaulted.setIv(payload.getIv());
        vaulted.setAuthTag(payload.getAuthTag());
        vaulted.setEphemeralPublicKey(payload.getEphemeralPublicKey());
        vaulted.setCryptoVersion(payload.getV());
        vaulted.setSentAt(payload.getSentAt());

        try {
            offlineRepo.save(vaulted);
        } catch (Exception e) {
            log.warn("Vault write failed for msgId {}: {}", payload.getMsgId(), e.getMessage());
        }

        String destination = "/topic/shadow-user-" + targetUsername;
        log.info("🚀 RELAY SUCCESS: Routing message from @{} to @{} in room {}", senderUsername, targetUsername, roomId);
        try {
            messagingTemplate.convertAndSend(destination, payload);
        } catch (Exception e) {
            log.warn("Live relay to {} failed, message remains vaulted: {}", destination, e.getMessage());
        }
    }

    private String resolveTarget(FortressPayload payload, String roomId, String senderUsername) {
        String supplied = payload.getTargetUsername();
        if (supplied != null && !supplied.isBlank()) {
            String target = supplied.trim().toLowerCase();
            if (!target.equals(senderUsername) && roomContainsUser(roomId, target)) {
                return target;
            }
        }
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
        private Integer v;
        private String roomId;
        private Long senderId;
        private String msgId;
        private String senderUsername;
        private String targetUsername;
        private String sentAt;
        private String ephemeralPublicKey;
        private String ciphertext;
        private String iv;
        private String authTag;
    }
}