package com.xindong.content.repository;

import com.xindong.content.entity.Anniversary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnniversaryRepository extends JpaRepository<Anniversary, Long> {

    Optional<Anniversary> findByIdAndCoupleId(Long id, Long coupleId);

    long countByCoupleId(Long coupleId);

    /**
     * 顺序：置顶true在前，再按创建时间倒序（同Service里做再做daysUntil二级排序）
     */
    List<Anniversary> findByCoupleIdOrderByIsTopDescCreatedAtDesc(Long coupleId);
}