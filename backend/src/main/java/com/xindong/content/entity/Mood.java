package com.xindong.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * M03 心情打卡实体
 * 🔴UNIQUE约束: (couple_id, partner_idx, date_str) 同日同方唯一
 */
@Data
@Entity
@NoArgsConstructor
@Table(name = "mood_checkins", uniqueConstraints = {
        @UniqueConstraint(name = "uk_couple_partner_date", columnNames = {"couple_id", "partner_idx", "date_str"})
})
@TableName("mood_checkins")
public class Mood {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "couple_id", nullable = false)
    private Long coupleId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * partner_idx = 1 或 2
     */
    @Column(name = "partner_idx", nullable = false)
    private Integer partnerIdx;

    /**
     * 日期字符串 yyyy-MM-dd （用于UNIQUE复合索引及按日查询）
     */
    @Column(name = "date_str", length = 10, nullable = false)
    private String dateStr;

    /**
     * 心情类型 1-6 字典值
     */
    @Column(name = "mood_type", nullable = false)
    private Integer moodType;

    /**
     * 缓存emoji（如 "😊"），冗余字段便于前端直接展示，不用查字典
     */
    @Column(name = "emoji", length = 8)
    private String emoji;

    @Column(name = "note", length = 100)
    private String note;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}