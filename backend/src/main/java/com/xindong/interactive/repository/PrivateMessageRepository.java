package com.xindong.interactive.repository;

import com.xindong.interactive.entity.PrivateMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PrivateMessageRepository extends JpaRepository<PrivateMessage, Long> {

    /**
     * 聊天历史分页（按时间正序）
     */
    Page<PrivateMessage> findByCoupleIdOrderByCreatedAtAsc(Long coupleId, Pageable pageable);

    /**
     * 聊天历史分页（按时间倒序 最新在前）
     */
    Page<PrivateMessage> findByCoupleIdOrderByCreatedAtDesc(Long coupleId, Pageable pageable);

    long countByCoupleIdAndReceiverIdAndIsReadFalse(Long coupleId, Long receiverId);

    @Modifying
    @Query("UPDATE PrivateMessage p SET p.isRead = true, p.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE p.coupleId = :coupleId AND p.receiverId = :me AND p.isRead = false")
    int markAllRead(@Param("coupleId") Long coupleId, @Param("me") Long me);

    long countByCoupleIdAndCreatedAtBetween(Long coupleId, java.time.LocalDateTime start, java.time.LocalDateTime end);
}