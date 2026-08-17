package com.xindong.incentive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xindong.incentive.config.WishState;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * M06 心愿单实体
 * 🔴红线4：status字段严格由 WishService 内部通过 Spring Statemachine 变更
 *   禁止外部直接 setStatus() 绕过状态机（代码中搜索"setStatus"确保只在 persistStateChange() 一处出现）
 */
@Data
@Entity
@NoArgsConstructor
@Table(name = "wishes", indexes = {
        @Index(name = "idx_couple_status_created", columnList = "couple_id,status,created_at DESC"),
        @Index(name = "idx_created_by", columnList = "created_by,status")
})
@TableName("wishes")
public class Wish {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "couple_id", nullable = false)
    private Long coupleId;

    @Column(name = "title", length = 50, nullable = false)
    private String title;

    /**
     * 兑换成本（心愿值/金币）：5-1000；创建时扣除押金；拒绝/取消退回
     */
    @Column(name = "cost", nullable = false)
    private Integer cost;

    @Column(name = "cover_img", length = 500)
    private String coverImg;

    /**
     * 分步列表JSON（可选）：例 [{"name":"买鲜花","done":false},{"name":"吃火锅","done":false}]
     * completeStep接口按idx勾完→全部勾完 COMPLETE事件迁移 COMPLETED
     */
    @Lob
    @Column(name = "steps_json", columnDefinition = "LONGTEXT")
    private String stepsJson;

    @Column(name = "total_steps")
    private Integer totalSteps;

    @Column(name = "completed_steps")
    private Integer completedSteps = 0;

    /**
     * 🔴红线4 状态字段 枚举：DRAFT / APPLYING / PENDING_APPROVAL / APPROVED / COMPLETED / REJECTED
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private WishState status = WishState.DRAFT;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

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