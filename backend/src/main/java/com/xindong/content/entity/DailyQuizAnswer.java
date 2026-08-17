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
 * M10 每日默契问答题目的情侣当日答题记录
 * 🔴红线2 核心表：双方答案分开存，直到两人都回答前 partnerAnswer* 字段在接口层一律返回null
 * UNIQUE(couple_id, question_id, date_str, partner_idx)
 */
@Data
@Entity
@NoArgsConstructor
@Table(name = "daily_quiz_answers", uniqueConstraints = {
        @UniqueConstraint(name = "uk_couple_q_date_partner",
                columnNames = {"couple_id", "question_id", "date_str", "partner_idx"})
})
@TableName("daily_quiz_answers")
public class DailyQuizAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "couple_id", nullable = false)
    private Long coupleId;

    /**
     * 题目id（对应SeedDataConstants里的quizQuestions.id）
     */
    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "date_str", length = 10, nullable = false)
    private String dateStr;

    @Column(name = "partner_idx", nullable = false)
    private Integer partnerIdx;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 用户选择的选项id 1/2/3/4（对应SeedData每题的4选项）
     */
    @Column(name = "option_id")
    private Integer optionId;

    /**
     * 用户的填空/文本答案内容（若题目是开放问答）
     */
    @Column(name = "answer_content", length = 500)
    private String answerContent;

    /**
     * 匹配度% 0-100：当双方都答完时由service结算填入
     * 我方未答完/对方未答完 → 都是null
     */
    @Column(name = "match_percent")
    private Integer matchPercent;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;
}