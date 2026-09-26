package com.SHADOW.P2P_SERVICE.Models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "offline_mailbox",
        indexes = {
                @Index(name = "idx_mailbox_timestamp", columnList = "timestamp"),
                @Index(name = "idx_mailbox_recipient", columnList = "recipientUsername"),
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

    @Column
    private String msgId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String encryptedPayload;

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    @Column(length = 40)
    private String sentAt;

    @Column
    private Integer cryptoVersion;

    @Column
    private String iv;

    @Column
    private String authTag;

    @Column(columnDefinition = "TEXT")
    private String ephemeralPublicKey;
}