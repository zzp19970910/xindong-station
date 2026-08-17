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
 * M05 日记实体
 * 正文最多5000字，图片最多9张
 */
@Data
@Entity
@NoArgsConstructor
@Table(name = "diaries", indexes = {
        @Index(name = "idx_couple_record_date", columnList = "couple_id,record_date DESC,created_at DESC"),
        @Index(name = "idx_couple_partner", columnList = "couple_id,partner_idx")
})
@TableName("diaries")
public class Diary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "couple_id", nullable = false)
    private Long coupleId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 冗余字段，等同于userId；部分历史代码/前端通过createdById访问
     */
    @Column(name = "user_id", insertable = false, updatable = false)
    private Long createdById;

    @Column(name = "partner_idx")
    private Integer partnerIdx;

    @Column(name = "title", length = 100)
    private String title;

    /**
     * 正文 5000字
     */
    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "mood")
    private Integer mood;

    /**
     * 图片URL，逗号分隔，最多9个
     */
    @Column(name = "image_urls", length = 5000)
    private String imageUrls;

    @Column(name = "image_count")
    private Integer imageCount = 0;

    @Column(name = "weather", length = 50)
    private String weather;

    @Column(name = "location", length = 100)
    private String location;

    @Column(name = "record_date")
    private LocalDate recordDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}