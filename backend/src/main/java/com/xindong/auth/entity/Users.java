package com.xindong.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_phone", columnList = "phone", unique = true),
        @Index(name = "idx_users_couple_id", columnList = "couple_id")
})
@TableName("users")
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(length = 11, nullable = false, unique = true)
    private String phone;

    @Column(length = 20)
    private String nickname;

    @Column(name = "avatar_url", length = 100)
    @TableField("avatar_url")
    private String avatarUrl;

    @Column(name = "couple_id")
    @TableField("couple_id")
    private Long coupleId;

    @Column(name = "partner_idx")
    @TableField("partner_idx")
    private Integer partnerIdx;

    @Column(name = "created_at")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}