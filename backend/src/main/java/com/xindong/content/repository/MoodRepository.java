package com.xindong.content.repository;

import com.xindong.content.entity.Mood;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MoodRepository extends JpaRepository<Mood, Long> {

    Optional<Mood> findByIdAndCoupleId(Long id, Long coupleId);

    void deleteByCoupleIdAndPartnerIdxAndDateStr(Long coupleId, Integer partnerIdx, String dateStr);

    List<Mood> findByCoupleIdAndPartnerIdxAndDateStr(Long coupleId, Integer partnerIdx, String dateStr);

    /**
     * 日期范围查询：yyyy-MM-dd字典序=日期序，直接用字符串比较可跨数据库（MySQL/H2/PostgreSQL通用）
     */
    @Query("SELECT m FROM Mood m WHERE m.coupleId = :coupleId " +
            "AND m.dateStr BETWEEN :startStr AND :endStr " +
            "ORDER BY m.dateStr DESC, m.id DESC")
    List<Mood> findByCoupleIdAndDateBetween(@Param("coupleId") Long coupleId,
                                            @Param("startStr") String startStr,
                                            @Param("endStr") String endStr);
}