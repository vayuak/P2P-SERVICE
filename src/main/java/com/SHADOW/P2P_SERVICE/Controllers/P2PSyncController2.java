package com.SHADOW.P2P_SERVICE.Controllers;

import com.SHADOW.P2P_SERVICE.Models.OfflineMessage;
import com.SHADOW.P2P_SERVICE.Repositories.OfflineMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*; // 🟢 Updated to include all annotations

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/v1/p2p")
@RequiredArgsConstructor
public class P2PSyncController2 {

    private final OfflineMessageRepository offlineRepo;

    @GetMapping("/sync")
    public ResponseEntity<List<P2PChatController.FortressPayload>> syncInbox(Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();

        String username = principal.getName().trim().toLowerCase();

        // Fetch all vaulted messages for this user across ALL rooms
        List<OfflineMessage> pending = offlineRepo.findByRecipientUsernameOrderByTimestampAsc(username);

        if (pending.isEmpty()) {
            return ResponseEntity.ok(new ArrayList<>());
        }

        List<P2PChatController.FortressPayload> payloads = new ArrayList<>();
        for (OfflineMessage msg : pending) {
            P2PChatController.FortressPayload payload = new P2PChatController.FortressPayload();
            payload.setMsgId(msg.getMsgId() != null ? msg.getMsgId() : "offline_" + msg.getId());
            payload.setRoomId(msg.getRoomId());
            payload.setSenderUsername(msg.getSenderUsername());
            payload.setTargetUsername(msg.getRecipientUsername());
            payload.setCiphertext(msg.getEncryptedPayload());
            payload.setIv(msg.getIv());
            payload.setAuthTag(msg.getAuthTag());
            payload.setEphemeralPublicKey(msg.getEphemeralPublicKey());
            payloads.add(payload);
        }

        // 🟢 REMOVED: offlineRepo.deleteAll(pending);
        // Messages stay in the vault until the client explicitly acknowledges them

        return ResponseEntity.ok(payloads);
    }

    // 🟢 NEW: The Acknowledgment Endpoint
    @PostMapping("/sync/ack")
    public ResponseEntity<?> acknowledgeSync(@RequestBody List<String> msgIds, Principal principal) {
        if (principal == null || msgIds == null || msgIds.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String username = principal.getName().trim().toLowerCase();

        // Safely shred only the messages the client confirmed they saved to their phone
        offlineRepo.deleteByRecipientUsernameAndMsgIdIn(username, msgIds);

        return ResponseEntity.ok().build();
    }
}