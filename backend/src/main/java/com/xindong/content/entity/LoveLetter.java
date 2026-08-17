package com.xindong.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * M12 情书实体
 * 🔴红线3 核心字段：
 *   content_cipher 存 LetterCryptoService AES-256-GCM 加密后的密文（明文绝不能入库）
 *   scheduled_at 定时寄出时间，is_time_capsule=true 时 详情接口schedAt未到 → 返回"********"代替明文
 */
@Data
@Entity
@NoArgsConstructor
@Table(name = "love_letters")
@TableName("love_letters")
public class LoveLetter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "couple_id", nullable = false)
    private Long coupleId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "partner_idx")
    private Integer partnerIdx;

    /**
     * 接收方userId（对方）
     */
    @Column(name = "receiver_id")
    private Long receiverId;

    @Column(name = "title", length = 100)
    private String title;

    /**
     * 🔴AES-256-GCM 加密后的密文字符串（明文content绝不入库）
     * ⚠️ 不写columnDefinition硬编码! 用@Lob让Hibernate方言自动选:
     *    PostgreSQL=TEXT / MySQL=LONGTEXT / H2=CLOB
     */
    @Lob
    @Column(name = "content_cipher", nullable = false)
    private String contentCipher;

    /**
     * 封面图片URL 最多1张
     */
    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    /**
     * 是否时间胶囊 true=定时开启 / false=立即可读
     */
    @Column(name = "is_time_capsule")
    private Boolean isTimeCapsule = false;

    /**
     * 定时开启时间（时间胶囊到了之后才返回AES解密明文）
     */
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    /**
     * 对方已读时间（已读回执 +2金币 LETTER_READ）
     */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    /**
     * 回复对方的父情书ID（可为空 表示首封）
     */
    @Column(name = "reply_to_id")
    private Long replyToId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}