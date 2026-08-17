package com.xindong.interactive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 批次5 M06 私信实体
 * 索引：couple_id + created_at 聊天倒序/正序；sender_id+is_read 未读计数命中
 */
@Data
@Entity
@NoArgsConstructor
@Table(name = "private_messages")
@TableName("private_messages")
public class PrivateMessage {

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
     * 接收方userId（=对方userId，用于快速未读计数）
     */
    @Column(name = "receiver_id")
    private Long receiverId;

    /**
     * 类型：text / image / emoji / system (4种)
     */
    @Column(name = "content_type", length = 10, nullable = false)
    private String contentType = "text";

    /**
     * 正文 最多2000字
     */
    @Lob
    @Column(name = "content", columnDefinition = "TEXT", length = 2000)
    private String content;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "emoji_code", length = 32)
    private String emojiCode;

    /**
     * 是否已读（true=已读 false=未读）
     */
    @Column(name = "is_read")
    private Boolean isRead = false;

    /**
     * 撤回状态（true=已撤回 前端显示"对方撤回一条消息"）
     */
    @Column(name = "is_recalled")
    private Boolean isRecalled = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = createdAt;
    }
}