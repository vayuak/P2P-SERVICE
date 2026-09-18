package com.SHADOW.P2P_SERVICE.Services;

import com.SHADOW.P2P_SERVICE.Repositories.OfflineMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailboxCleanupService {

    private final OfflineMessageRepository offlineMessageRepository;

    /**
     * Runs automatically every hour at the top of the hour.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void purgeExpiredOfflineMessages() {
        // 🛡️ CHANGED: Now strictly 37 hours to keep the DB ultra-lightweight
        LocalDateTime expiryThreshold = LocalDateTime.now().minusHours(37);

        log.info("🧹 [MAILBOX CLEANUP] Waking up. Searching for vaulted messages older than 37 hours (Before {})...", expiryThreshold);

        try {
            int deletedCount = offlineMessageRepository.deleteExpiredMessages(expiryThreshold);
            if (deletedCount > 0) {
                log.info("🚨 [MAILBOX CLEANUP] Successfully shredded {} expired offline messages.", deletedCount);
            } else {
                log.debug("✨ [MAILBOX CLEANUP] Mailbox is clean. No expired messages found.");
            }
        } catch (Exception e) {
            log.error("❌ [MAILBOX CLEANUP] Failed to purge expired messages: {}", e.getMessage());
        }
    }
}