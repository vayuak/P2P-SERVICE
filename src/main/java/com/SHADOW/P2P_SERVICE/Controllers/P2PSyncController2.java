package com.SHADOW.P2P_SERVICE.Controllers;

import com.SHADOW.P2P_SERVICE.Models.OfflineMessage;
import com.SHADOW.P2P_SERVICE.Repositories.OfflineMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/p2p")
@RequiredArgsConstructor
@Slf4j
public class P2PSyncController2 {

    private static final int MAX_SYNC_BATCH = 300;
    private final OfflineMessageRepository offlineRepo;

    private String resolveCaller(String headerUser, Principal principal) {
        if (headerUser != null && !headerUser.isBlank()) {
            return headerUser.trim().toLowerCase();
        }
        if (principal != null && principal.getName() != null) {
            return principal.getName().trim().toLowerCase();
        }
        return null;
    }

    @GetMapping("/sync")
    public ResponseEntity<?> syncInbox(
            @RequestHeader(value = "X-User-Id", required = false) String headerUser, // 🟢 FIXED: Matches Gateway Header
            Principal principal) {

        String username = resolveCaller(headerUser, principal);
        if (username == null) {
            log.warn("Sync rejected: could not identify caller. Verify gateway is forwarding X-User-Id.");
            return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        }

        List<OfflineMessage> pending = offlineRepo.findByRecipientUsernameOrderByTimestampAsc(username);
        if (pending.isEmpty()) {
            return ResponseEntity.ok(new ArrayList<>());
        }
        if (pending.size() > MAX_SYNC_BATCH) {
            pending = pending.subList(0, MAX_SYNC_BATCH);
        }

        List<P2PChatController.FortressPayload> payloads = new ArrayList<>();
        for (OfflineMessage msg : pending) {
            P2PChatController.FortressPayload p = new P2PChatController.FortressPayload();
            p.setV(msg.getCryptoVersion() != null ? msg.getCryptoVersion() : 2);
            p.setMsgId(msg.getMsgId() != null ? msg.getMsgId() : "offline_" + msg.getId());
            p.setRoomId(msg.getRoomId());
            p.setSenderUsername(msg.getSenderUsername());
            p.setTargetUsername(msg.getRecipientUsername());
            p.setCiphertext(msg.getEncryptedPayload());
            p.setIv(msg.getIv());
            p.setAuthTag(msg.getAuthTag());
            p.setEphemeralPublicKey(msg.getEphemeralPublicKey());
            p.setSentAt(msg.getSentAt() != null
                    ? msg.getSentAt()
                    : msg.getTimestamp().toString());
            payloads.add(p);
        }

        log.info("Sync: handing {} vaulted messages to {}", payloads.size(), username);
        return ResponseEntity.ok(payloads);
    }

    @PostMapping("/sync/ack")
    public ResponseEntity<?> acknowledgeSync(
            @RequestBody List<String> msgIds,
            @RequestHeader(value = "X-User-Id", required = false) String headerUser, // 🟢 FIXED: Matches Gateway Header
            Principal principal) {

        String username = resolveCaller(headerUser, principal);
        if (username == null) {
            return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        }
        if (msgIds == null || msgIds.isEmpty()) {
            return ResponseEntity.ok(Map.of("deleted", 0));
        }

        int deleted = offlineRepo.deleteByRecipientUsernameAndMsgIdIn(username, msgIds);
        log.info("Sync ack: shredded {} messages for {}", deleted, username);
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }
}