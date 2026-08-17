package com.xindong.content.repository;

import com.xindong.content.entity.DailyQuizAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DailyQuizAnswerRepository extends JpaRepository<DailyQuizAnswer, Long> {

    /**
     * 今日(含指定date_str)某情侣的答题结果
     * 用于今日详情页聚合
     */
    @Query("SELECT a FROM DailyQuizAnswer a WHERE a.coupleId = :coupleId AND a.dateStr = :dateStr ORDER BY a.questionId ASC, a.partnerIdx ASC")
    List<DailyQuizAnswer> findCoupleAnswersOnDate(@Param("coupleId") Long coupleId, @Param("dateStr") String dateStr);

    /**
     * 查找单题情侣两人的答案（用于结算匹配度）
     */
    List<DailyQuizAnswer> findByCoupleIdAndQuestionIdAndDateStr(Long coupleId, Long questionId, String dateStr);

    @Query("SELECT a FROM DailyQuizAnswer a WHERE a.coupleId = :coupleId AND a.dateStr BETWEEN :startStr AND :endStr ORDER BY a.dateStr DESC, a.questionId ASC")
    List<DailyQuizAnswer> findCoupleAnswersOnDateBetween(@Param("coupleId") Long coupleId,
                                                         @Param("startStr") String startStr,
                                                         @Param("endStr") String endStr);

    /**
     * 找我今日对某题已提交的答案（幂等）
     */
    Optional<DailyQuizAnswer> findByCoupleIdAndQuestionIdAndDateStrAndPartnerIdx(
            Long coupleId, Long questionId, String dateStr, Integer partnerIdx);
}