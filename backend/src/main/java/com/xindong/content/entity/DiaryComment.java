package com.xindong.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 日记评论实体（批次4 M05-6）
 * 最多300字
 */
@Data
@Entity
@NoArgsConstructor
@Table(name = "diary_comments")
@TableName("diary_comments")
public class DiaryComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "diary_id", nullable = false)
    private Long diaryId;

    @Column(name = "couple_id", nullable = false)
    private Long coupleId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "partner_idx")
    private Integer partnerIdx;

    /**
     * 评论内容 最多300字
     */
    @Column(name = "content", length = 300, nullable = false)
    private String content;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}