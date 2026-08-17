package com.xindong.interactive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * M07 破冰转盘对话会话（每对情侣按自然日一个session，存spin剩余次数 + 当前抽到的任务id）
 */
@Data
@Entity
@NoArgsConstructor
@Table(name = "icebreak_sessions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_couple_date", columnNames = {"couple_id", "date_str"})
})
@TableName("icebreak_sessions")
public class IcebreakSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "couple_id", nullable = false)
    private Long coupleId;

    @Column(name = "date_str", length = 10, nullable = false)
    private String dateStr;

    /**
     * 剩余spin次数（每日免费3次 + 每完成1次任务+2次）
     */
    @Column(name = "spins_left")
    private Integer spinsLeft = 3;

    /**
     * 当前抽到且未完成的任务id；完成后置null
     */
    @Column(name = "current_task_id")
    private Long currentTaskId;

    @Column(name = "current_task_name", length = 100)
    private String currentTaskName;

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