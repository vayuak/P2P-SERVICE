package com.SHADOW.P2P_SERVICE.Controllers;

import com.SHADOW.P2P_SERVICE.Models.OfflineMessage;
import com.SHADOW.P2P_SERVICE.Repositories.OfflineMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        // Note: You must add findByRecipientUsernameOrderByTimestampAsc to your repository
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

        // Delete them from the vault so they aren't downloaded twice
        offlineRepo.deleteAll(pending);

        return ResponseEntity.ok(payloads);
    }
}