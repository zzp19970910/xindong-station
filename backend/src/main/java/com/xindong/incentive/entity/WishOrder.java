package com.xindong.incentive.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 🔴B7红线专用：WishOrder 兑换订单（三操作同事务第3步：扣币后必须写订单）
 * 唯一索引：(wishId, coupleId) 防止并发重复兑换
 */
@Entity
@Table(name = "wish_orders", uniqueConstraints = {
        @UniqueConstraint(name = "uk_wish_couple", columnNames = {"wishId", "coupleId"})
})
@Getter
@Setter
public class WishOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long wishId;

    @Column(nullable = false)
    private Long coupleId;

    private Long createdBy;

    private Long approverId;

    private Integer cost;

    private String titleSnap;

    private LocalDateTime createdAt;

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}