package com.SHADOW.P2P_SERVICE.Controllers;

import com.SHADOW.P2P_SERVICE.Clients.UserCatalogClient;
import com.SHADOW.P2P_SERVICE.Services.FirebasePushService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/v1/p2p/notifications")
public class NotificationTriggerController {

    private final FirebasePushService firebasePushService;
    private final UserCatalogClient userCatalogClient;

    public NotificationTriggerController(FirebasePushService firebasePushService, UserCatalogClient userCatalogClient) {
        this.firebasePushService = firebasePushService;
        this.userCatalogClient = userCatalogClient;
    }

    @PostMapping("/wake-peer")
    public ResponseEntity<?> wakePeerDevice(@RequestParam String targetUsername, @RequestParam String senderUsername) {
        try {
            String targetFcmToken = userCatalogClient.fetchTokenFromUserCatalog(targetUsername);

            if (targetFcmToken != null && !targetFcmToken.isBlank()) {
                firebasePushService.wakeUpUser(
                        targetFcmToken,
                        "WarnPack",
                        "🔒 You have a new encrypted message from @" + senderUsername
                );
                return ResponseEntity.ok(Map.of("status", "SUCCESS"));
            }
            return ResponseEntity.badRequest().body(Map.of("error", "User has no device token"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}