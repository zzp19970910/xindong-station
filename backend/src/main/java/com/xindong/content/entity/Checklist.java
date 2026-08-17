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
 * M09 清单（心动清单/心愿单/ToDoList）实体
 * 字段对齐 SeedRunner 预置的30条模板
 */
@Data
@Entity
@NoArgsConstructor
@Table(name = "checklists")
@TableName("checklists")
public class Checklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "couple_id")
    private Long coupleId; // null = 系统预置模板 (SeedRunner写入时couple_id=null + is_preset=1)

    @Column(name = "is_preset")
    private Boolean isPreset = false;

    @Column(name = "category", length = 20)
    private String category; // love/daily/travel/food/milestone 对齐SeedDataConstants

    @Column(name = "title", length = 100, nullable = false)
    private String title;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "icon", length = 32)
    private String icon; // emoji或图标名

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    /**
     * 完成状态 true=已完成 / false=未完成
     */
    @Column(name = "is_done")
    private Boolean isDone = false;

    /**
     * 里程碑奖励（预置清单用）：完成时空投 milestone_9_* 奖励多少金币
     * SeedDataConstants CHECKLISTS_MILESTONE_BONUS定义了三档：50 / 100 / 200
     */
    @Column(name = "milestone_bonus")
    private Integer milestoneBonus;

    /**
     * 创建人userId（用户自己从模板复制新增的可追踪）
     */
    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "completed_at")
    private LocalDate completedAt;

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