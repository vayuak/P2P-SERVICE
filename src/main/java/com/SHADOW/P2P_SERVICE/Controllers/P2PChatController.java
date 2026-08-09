package com.SHADOW.P2P_SERVICE.Controllers;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@Slf4j
@RequiredArgsConstructor
public class P2PChatController {

    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/shadow/send")
    public void relayEncryptedMessage(@Payload FortressPayload payload) {
        // The server knows WHO is talking, but has absolutely zero idea WHAT is inside.
        log.info("🛡️ [ZERO-TRUST RELAY] Routing AEAD packet for Room: {} | Sender: {}",
                payload.getRoomId(), payload.getSenderId());

        // Directly route to the active STOMP subscriber. NO DATABASE SAVING.
        String destination = "/topic/shadow-" + payload.getRoomId();
        messagingTemplate.convertAndSend(destination, payload);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FortressPayload {
        private String roomId;
        private Long senderId;

        // 🔒 Cryptographic Elements (Server is blind to this)
        private String ephemeralPublicKey; // For Perfect Forward Secrecy (ECDH)
        private String ciphertext;         // The AES-GCM encrypted message + sequence + mediaUrl
        private String iv;                 // Initialization Vector
        private String authTag;            // AEAD Integrity verification tag
    }

}