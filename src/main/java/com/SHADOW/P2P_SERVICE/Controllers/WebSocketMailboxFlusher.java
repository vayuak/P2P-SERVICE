package com.SHADOW.P2P_SERVICE.Controllers;

import com.SHADOW.P2P_SERVICE.Models.OfflineMessage;
import com.SHADOW.P2P_SERVICE.Repositories.OfflineMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Delivers vaulted messages when a recipient subscribes to a room.
 *
 * TWO BUGS FIXED
 * --------------
 * 1. RACE: the old version published inside the SessionSubscribeEvent handler,
 *    which runs on the inbound channel thread while the SUBSCRIBE frame is
 *    still being processed. The subscription is not guaranteed to be registered
 *    with the broker yet, so sends could land before anyone was listening. The
 *    handler is now @Async with a short settle delay, so the subscription is
 *    live before the first frame goes out.
 *
 * 2. DATA LOSS: the old version called deleteAll() immediately after sending,
 *    with no confirmation. Combined with bug 1 this permanently destroyed
 *    messages that were never delivered. Rows are now deleted one at a time
 *    only after convertAndSend returns without throwing, and any row that
 *    fails is left in the mailbox for the next subscribe.
 *
 * REMAINING LIMITATION: convertAndSend returning cleanly means the broker
 * accepted the frame, not that the device rendered it. Truly reliable delivery
 * needs client acknowledgements — the device posts back the msgIds it stored
 * and the server deletes those. That is the next step, and the msgId column
 * added to OfflineMessage exists to support it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketMailboxFlusher {

    private static final long SUBSCRIPTION_SETTLE_MS = 250L;

    private final OfflineMessageRepository offlineMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Async
    @EventListener
    public void handleSessionSubscribeEvent(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = headerAccessor.getDestination();

        if (headerAccessor.getUser() == null || destination == null) return;
        if (!destination.startsWith("/topic/shadow-")) return;

        String username = headerAccessor.getUser().getName().trim().toLowerCase();
        String roomId = destination.substring("/topic/shadow-".length()).trim().toLowerCase();

        List<OfflineMessage> pending =
                offlineMessageRepository.findByRecipientUsernameAndRoomIdOrderByTimestampAsc(username, roomId);

        if (pending.isEmpty()) return;

        try {
            Thread.sleep(SUBSCRIPTION_SETTLE_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return;
        }

        List<Long> delivered = new ArrayList<>();

        for (OfflineMessage msg : pending) {
            try {
                P2PChatController.FortressPayload payload = new P2PChatController.FortressPayload();
                payload.setMsgId(msg.getMsgId() != null ? msg.getMsgId() : "offline_" + msg.getId());
                payload.setRoomId(msg.getRoomId());
                payload.setSenderUsername(msg.getSenderUsername());
                payload.setTargetUsername(msg.getRecipientUsername());
                payload.setCiphertext(msg.getEncryptedPayload());
                payload.setIv(msg.getIv());
                payload.setAuthTag(msg.getAuthTag());
                payload.setEphemeralPublicKey(msg.getEphemeralPublicKey());

                messagingTemplate.convertAndSend(destination, payload);
                delivered.add(msg.getId());
            } catch (Exception e) {
                // Leave this row in the mailbox and stop: preserving order
                // matters more than draining the queue.
                log.error("Failed to deliver mailbox message {} to {}: {}",
                        msg.getId(), username, e.getMessage());
                break;
            }
        }

        if (!delivered.isEmpty()) {
            offlineMessageRepository.deleteByIdIn(delivered);
            log.info("Flushed {} of {} vaulted messages for {} in room {}",
                    delivered.size(), pending.size(), username, roomId);
        }
    }
}
