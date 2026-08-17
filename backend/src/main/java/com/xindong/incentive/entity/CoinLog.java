package com.xindong.incentive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "coin_logs")
@TableName("coin_logs")
public class CoinLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "couple_id", nullable = false)
    @TableField("couple_id")
    private Long coupleId;

    @Column(length = 50, nullable = false)
    private String reason;

    @Column(name = "reason_label", length = 50)
    @TableField("reason_label")
    private String reasonLabel;

    @Column(nullable = false)
    private Integer delta;

    @Column(name = "balance_after", nullable = false)
    @TableField("balance_after")
    private Integer balanceAfter;

    @Column(name = "from_user_id")
    @TableField("from_user_id")
    private Long fromUserId;

    @Column(name = "from_partner")
    @TableField("from_partner")
    private Long fromPartner;

    @Column(name = "biz_id", length = 100)
    @TableField("biz_id")
    private String bizId;

    @Column(name = "date_str", length = 10)
    @TableField("date_str")
    private String dateStr;

    @Column(name = "created_at")
    @TableField("created_at")
    private LocalDateTime createdAt;
}