package com.xindong.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * M04 纪念日实体
 * 每对情侣上限50个
 */
@Data
@Entity
@NoArgsConstructor
@Table(name = "anniversaries", indexes = {
        @Index(name = "idx_couple_top", columnList = "couple_id,is_top DESC,created_at DESC")
})
@TableName("anniversaries")
public class Anniversary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "couple_id", nullable = false)
    private Long coupleId;

    @Column(name = "title", length = 30, nullable = false)
    private String title;

    /**
     * love/travel/birthday/anniversary/other
     */
    @Column(name = "type", length = 20, nullable = false)
    private String type;

    @Column(name = "emoji", length = 8)
    private String emoji;

    /**
     * 目标日期（不能过去 否则20401）
     */
    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "note", length = 200)
    private String note;

    @Column(name = "is_top")
    private Boolean isTop = false;

    /**
     * countdown/countup 两种展示
     */
    @Column(name = "display_mode", length = 15)
    private String displayMode;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}