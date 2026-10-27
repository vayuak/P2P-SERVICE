package com.SHADOW.P2P_SERVICE.Controllers;

import com.SHADOW.P2P_SERVICE.Clients.UserCatalogClient;
import com.SHADOW.P2P_SERVICE.Models.OfflineMessage;
import com.SHADOW.P2P_SERVICE.Repositories.OfflineMessageRepository;
import com.SHADOW.P2P_SERVICE.Services.FirebasePushService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@Slf4j
@RequiredArgsConstructor
public class P2PChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final OfflineMessageRepository offlineRepo;
    private final UserCatalogClient userCatalogClient;
    private final FirebasePushService firebasePushService;

    @MessageMapping("/shadow/send")
    public void relayEncryptedMessage(@Payload FortressPayload payload, Principal principal) {
        if (principal == null) {
            log.warn("Rejected unauthenticated relay attempt.");
            return;
        }

        String senderUsername = principal.getName().trim().toLowerCase();

        if (payload.getRoomId() == null || payload.getRoomId().isBlank()) {
            log.warn("Rejected relay with no roomId from {}", senderUsername);
            reply(senderUsername, "NACK", payload.getMsgId(), "MISSING_ROOM_ID");
            return;
        }
        if (payload.getCiphertext() == null || payload.getCiphertext().isBlank()) {
            log.warn("Rejected relay with empty ciphertext from {}", senderUsername);
            reply(senderUsername, "NACK", payload.getMsgId(), "MISSING_CIPHERTEXT");
            return;
        }

        String roomId = payload.getRoomId().trim().toLowerCase();
        String targetUsername = resolveTarget(payload, roomId, senderUsername);

        if (targetUsername == null) {
            log.warn("Could not resolve recipient for room {} (sender {}). Dropping.", roomId, senderUsername);
            reply(senderUsername, "NACK", payload.getMsgId(), "INVALID_TARGET");
            return;
        }
        if (!roomContainsUser(roomId, senderUsername)) {
            log.warn("Rejected relay: {} is not a participant in room {}", senderUsername, roomId);
            reply(senderUsername, "NACK", payload.getMsgId(), "NOT_IN_ROOM");
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

        boolean stored = false;
        try {
            offlineRepo.save(vaulted);
            stored = true;
            String targetFcmToken = userCatalogClient.fetchTokenFromUserCatalog(targetUsername);
            firebasePushService.wakeUpUser(
                    targetFcmToken,
                    "WarnPack",
                    "🔒 You have a new encrypted message from @" + senderUsername
            );
        } catch (Exception e) {
            log.warn("Vault write failed for msgId {}: {}", payload.getMsgId(), e.getMessage());
        }

        String destination = "/topic/shadow-user-" + targetUsername;
        log.debug("RELAY SUCCESS: Routing message from @{} to @{} in room {}", senderUsername, targetUsername, roomId);
        try {
            messagingTemplate.convertAndSend(destination, payload);
        } catch (Exception e) {
            log.warn("Live relay to {} failed, message remains vaulted: {}", destination, e.getMessage());
        }

        reply(senderUsername, stored ? "ACK" : "NACK", payload.getMsgId(), stored ? null : "STORE_FAILED");
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

    private void reply(String user, String type, String msgId, String reason) {
        if (user == null || msgId == null) return;
        Map<String, String> m = new HashMap<>();
        m.put("type", type);
        m.put("msgId", msgId);
        if (reason != null) m.put("reason", reason);
        messagingTemplate.convertAndSend("/topic/shadow-user-" + user, m);
    }

    @MessageMapping("/shadow/receipt")
    public void relayReceipt(@Payload ReceiptPayload r, Principal principal) {
        if (principal == null || r.getMsgId() == null || r.getTargetUsername() == null) return;
        String me = principal.getName().trim().toLowerCase();
        String originalSender = r.getTargetUsername().trim().toLowerCase();
        String status = "read".equals(r.getStatus()) ? "read" : "delivered";

        offlineRepo.deleteByRecipientUsernameAndMsgIdIn(me, List.of(r.getMsgId()));

        Map<String, String> m = new HashMap<>();
        m.put("type", "RECEIPT");
        m.put("msgId", r.getMsgId());
        m.put("status", status);
        m.put("from", me);
        messagingTemplate.convertAndSend("/topic/shadow-user-" + originalSender, m);
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
        private String targetFcmToken;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceiptPayload {
        private String msgId;
        private String targetUsername;
        private String status;
    }
}