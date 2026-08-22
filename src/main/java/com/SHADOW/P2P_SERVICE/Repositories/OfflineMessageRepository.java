package com.SHADOW.P2P_SERVICE.Repositories;

import com.SHADOW.P2P_SERVICE.Models.OfflineMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OfflineMessageRepository extends JpaRepository<OfflineMessage, Long> {

    List<OfflineMessage> findByRecipientUsernameAndRoomIdOrderByTimestampAsc(String recipientUsername, String roomId);

    @Transactional
    void deleteByRecipientUsername(String recipientUsername);

    @Modifying
    @Transactional
    @Query("DELETE FROM OfflineMessage o WHERE o.timestamp < :expiryDate")
    int deleteExpiredMessages(@Param("expiryDate") LocalDateTime expiryDate);

    // Delete by explicit id set, so the flusher can remove only what it has
    // actually handed to the broker.
    @Modifying
    @Transactional
    @Query("DELETE FROM OfflineMessage o WHERE o.id IN :ids")
    int deleteByIdIn(@Param("ids") List<Long> ids);
}
