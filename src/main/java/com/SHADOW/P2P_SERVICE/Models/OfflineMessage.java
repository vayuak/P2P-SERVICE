package com.SHADOW.P2P_SERVICE.Models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Relayed ciphertext held for an offline recipient.
 *
 * INDEXES ARE NOW ANNOTATED.
 *
 * Hibernate's ddl-auto=update creates indexes declared in @Table, but never
 * ones you only wrote in a migration script. Declaring them here means that on
 * a FRESH database (one where you dropped everything) Hibernate builds the
 * whole table correctly and you need to run no SQL at all.
 *
 * Why these two:
 *   - idx_mailbox_timestamp: MailboxCleanupService range-scans on timestamp
 *     every hour. Without it that is a full table scan.
 *   - idx_mailbox_lookup: WebSocketMailboxFlusher looks up by
 *     (recipient_username, room_id) on every subscribe.
 *
 * NOTE ON EXISTING DATABASES: if the offline_mailbox table already exists,
 * Hibernate will add the indexes but will NOT drop the legacy `ciphertext`
 * column or relax its NOT NULL constraint. That still needs Block 2 of the
 * migration. On a dropped/fresh database, none of that applies.
 */
@Entity
@Table(
        name = "offline_mailbox",
        indexes = {
                @Index(name = "idx_mailbox_timestamp", columnList = "timestamp"),
                @Index(name = "idx_mailbox_lookup", columnList = "recipientUsername, roomId")
        }
)
@Data
public class OfflineMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String senderUsername;

    @Column(nullable = false)
    private String recipientUsername;

    @Column(nullable = false)
    private String roomId;

    // Client-generated id, used for dedupe on the receiving device.
    @Column
    private String msgId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String encryptedPayload;

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    // Base64 nacl.box nonce, 24 bytes.
    @Column
    private String iv;

    // Retained for wire compatibility only. nacl.box carries its Poly1305 tag
    // inside the ciphertext, so this is always empty now.
    @Column
    private String authTag;

    // Base64 X25519 public key of the sender, so a vaulted message can be
    // opened without a live handshake. The recipient verifies it against the
    // key directory before trusting it.
    @Column(columnDefinition = "TEXT")
    private String ephemeralPublicKey;
}
