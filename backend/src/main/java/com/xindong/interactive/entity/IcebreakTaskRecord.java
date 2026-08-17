package com.xindong.interactive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * M07 破冰任务完成记录（完成后存感悟；完成1次+2spin次数 + 3内容金币）
 */
@Data
@Entity
@NoArgsConstructor
@Table(name = "icebreak_task_records", indexes = {
        @Index(name = "idx_couple_created", columnList = "couple_id,created_at DESC")
})
@TableName("icebreak_task_records")
public class IcebreakTaskRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "couple_id", nullable = false)
    private Long coupleId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "task_name", length = 100)
    private String taskName;

    @Column(name = "finished_by_id")
    private Long finishedById;

    @Column(name = "partner_idx")
    private Integer partnerIdx;

    /**
     * 完成感悟文字 ≤ 500字
     */
    @Column(name = "reflection", length = 500)
    private String reflection;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}