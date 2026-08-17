package com.xindong.incentive.repository;

import com.xindong.incentive.entity.CoinLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CoinLogRepository extends JpaRepository<CoinLog, Long> {

    List<CoinLog> findByCoupleId(Long coupleId);

    Page<CoinLog> findByCoupleIdOrderByCreatedAtDesc(Long coupleId, Pageable pageable);

    @Query("SELECT cl.reason, SUM(cl.delta) FROM CoinLog cl " +
            "WHERE cl.coupleId = :coupleId AND cl.dateStr = :dateStr AND cl.delta > 0 " +
            "GROUP BY cl.reason")
    List<Object[]> sumIncomeByReason(@Param("coupleId") Long coupleId, @Param("dateStr") String dateStr);

    @Query("SELECT COUNT(cl) > 0 FROM CoinLog cl WHERE cl.coupleId = :coupleId " +
            "AND cl.reason = :reason AND cl.dateStr = :dateStr")
    boolean existsByCoupleIdAndReasonAndDateStr(@Param("coupleId") Long coupleId,
                                                @Param("reason") String reason,
                                                @Param("dateStr") String dateStr);
}