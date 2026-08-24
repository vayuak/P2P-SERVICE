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

import java.security.Principal;

@Controller
@Slf4j
@RequiredArgsConstructor
public class P2PChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry userRegistry;
    private final OfflineMessageRepository offlineRepo;

    @MessageMapping("/shadow/send")
    public void relayEncryptedMessage(@Payload FortressPayload payload, Principal principal) {
        if (principal == null) {
            log.warn("Rejected unauthenticated relay attempt.");
            return;
        }

        // Trust authenticated principal only
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

        String destination = "/topic/shadow-" + roomId;
        boolean isTargetOnline = userRegistry.getUser(targetUsername) != null;

        if (isTargetOnline) {
            log.info("Relaying packet for room {} to online recipient {}", roomId, targetUsername);
            messagingTemplate.convertAndSend(destination, payload);
        } else {
            log.info("Recipient {} offline. Vaulting payload to encrypted mailbox.", targetUsername);
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