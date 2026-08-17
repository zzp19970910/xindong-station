package com.xindong.content.repository;

import com.xindong.content.entity.Checklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChecklistRepository extends JpaRepository<Checklist, Long> {

    /**
     * 红线归属：按id+coupleId查询（preset模板coupleId=null也允许findById传null匹配）
     */
    @Query("SELECT c FROM Checklist c WHERE c.id = :id AND (c.coupleId = :coupleId OR c.coupleId IS NULL)")
    Optional<Checklist> findByIdAndCoupleIdOrPreset(@Param("id") Long id, @Param("coupleId") Long coupleId);

    /**
     * 查我的清单 = coupleId匹配的用户自定义 + 系统预置(coupleId IS NULL) UNION ALL
     * 分类category过滤 & 状态isDone过滤
     */
    @Query("SELECT c FROM Checklist c WHERE (c.coupleId = :coupleId OR c.coupleId IS NULL) " +
            "AND (:category IS NULL OR :category = '' OR c.category = :category) " +
            "AND (:onlyDone IS NULL OR c.isDone = :onlyDone) " +
            "ORDER BY c.isPreset DESC, c.sortOrder ASC, c.id ASC")
    List<Checklist> findMyChecklists(@Param("coupleId") Long coupleId,
                                     @Param("category") String category,
                                     @Param("onlyDone") Boolean onlyDone);

    @Query("SELECT c FROM Checklist c WHERE c.coupleId IS NULL AND c.isPreset = true " +
            "AND (:category IS NULL OR :category = '' OR c.category = :category) " +
            "AND (:onlyDone IS NULL OR c.isDone = :onlyDone) " +
            "ORDER BY c.sortOrder ASC, c.id ASC")
    List<Checklist> findPresetOnly(@Param("category") String category,
                                   @Param("onlyDone") Boolean onlyDone);

    /**
     * 按coupleId查用户自己从模板勾选完成的 已完成数量（里程碑3统计）
     */
    @Query("SELECT COUNT(c) FROM Checklist c WHERE c.coupleId = :coupleId AND c.isDone = true AND c.milestoneBonus IS NOT NULL")
    long countDoneWithMilestoneBonus(@Param("coupleId") Long coupleId);
}