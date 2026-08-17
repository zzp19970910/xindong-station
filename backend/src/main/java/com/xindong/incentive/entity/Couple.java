package com.xindong.incentive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "couples", indexes = {
        @Index(name = "idx_invite_p1", columnList = "invite_code_p1", unique = true),
        @Index(name = "idx_invite_p2", columnList = "invite_code_p2", unique = true)
})
@TableName("couples")
public class Couple {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "together_date")
    @TableField("together_date")
    private LocalDate togetherDate;

    @Column(name = "invite_code_p1", length = 6, unique = true)
    @TableField("invite_code_p1")
    private String inviteCodeP1;

    @Column(name = "invite_code_p2", length = 6, unique = true)
    @TableField("invite_code_p2")
    private String inviteCodeP2;

    @Column(name = "coins_total", nullable = false)
    @TableField("coins_total")
    private Integer coinsTotal = 0;

    @Column(length = 20)
    private String theme = "default";

    @Column(name = "cooling_until")
    @TableField("cooling_until")
    private LocalDateTime coolingUntil;

    @Column(name = "cooling_lock_until")
    @TableField("cooling_lock_until")
    private LocalDateTime coolingLockUntil;

    @Column(name = "sign_streak")
    @TableField("sign_streak")
    private Integer signStreak = 0;

    @Column(name = "created_at")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isCoolingActive() {
        return coolingUntil != null && LocalDateTime.now().isBefore(coolingUntil);
    }
}