package com.SHADOW.P2P_SERVICE.Services;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FirebasePushService {

    public void wakeUpUser(String targetFcmToken, String title, String body) {
        if (targetFcmToken == null || targetFcmToken.isBlank()) return;

        try {
            // 🟢 Extract the sender's username from the body so the app knows who to chat with
            String senderUsername = "";
            if (body.contains("@")) {
                senderUsername = body.split("@")[1].split(" ")[0].trim();
            }

            // 🟢 Determine the route based on the title
            String route = title.contains("Radar") ? "Campfire" : "Chat";

            Message message = Message.builder()
                    .setToken(targetFcmToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    // 🟢 ADD THIS: Hidden data the app reads when tapped
                    .putData("route", route)
                    .putData("username", senderUsername)
                    .build();

            FirebaseMessaging.getInstance().send(message);
            log.info("FCM Ping sent to wake up device for route: {}", route);
        } catch (Exception e) {
            log.warn("FCM Push Failed: {}", e.getMessage());
        }
    }
}