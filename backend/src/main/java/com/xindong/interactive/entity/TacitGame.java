package com.xindong.interactive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * M08 默契问答游戏对局（发起一次 → "我猜TA会选啥？" + TA同步作答 → 匹配度统计）
 * 题目从SeedData 210题中随机抽取8题/局
 */
@Data
@Entity
@NoArgsConstructor
@Table(name = "tacit_games")
@TableName("tacit_games")
public class TacitGame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "couple_id", nullable = false)
    private Long coupleId;

    @Column(name = "created_by_id")
    private Long createdById; // 发起方userId

    /**
     * 题目id列表 JSON字符串 "1,2,3,4,5,6,7,8"
     */
    @Column(name = "question_ids_cipher", length = 500)
    private String questionIdsStr;

    /**
     * 发起方自己选的每题答案：Map<qid, {myOptionId, guessPartnerOptionId}> 序列化为JSON
     * ⚠️ 不写columnDefinition硬编码! 用@Lob让Hibernate方言自动选:
     *    PostgreSQL=TEXT / MySQL=LONGTEXT / H2=CLOB
     */
    @Lob
    @Column(name = "p1_answers")
    private String p1Answers;

    /**
     * ⚠️ 不写columnDefinition硬编码! 用@Lob让Hibernate方言自动选:
     *    PostgreSQL=TEXT / MySQL=LONGTEXT / H2=CLOB
     */
    @Lob
    @Column(name = "p2_answers")
    private String p2Answers;

    @Column(name = "p1_partner_idx")
    private Integer p1PartnerIdx;

    @Column(name = "p2_partner_idx")
    private Integer p2PartnerIdx;

    /**
     * 0=waiting 等待对方答题 / 1=done 双方都答完了
     */
    @Column(name = "game_status")
    private Integer gameStatus = 0;

    /**
     * 双方都答完后结算的默契度%（0-100）
     */
    @Column(name = "match_percent")
    private Integer matchPercent;

    @Column(name = "p1_finished_at")
    private LocalDateTime p1FinishedAt;

    @Column(name = "p2_finished_at")
    private LocalDateTime p2FinishedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Transient
    private transient List<Long> questionIdsMem; // 运行时缓存，不进库

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}