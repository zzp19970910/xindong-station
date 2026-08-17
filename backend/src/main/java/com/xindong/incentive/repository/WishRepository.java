package com.xindong.incentive.repository;

import com.xindong.incentive.config.WishState;
import com.xindong.incentive.entity.Wish;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WishRepository extends JpaRepository<Wish, Long> {

    List<Wish> findByCoupleIdAndStatusOrderByCreatedAtDesc(Long coupleId, WishState status);

    List<Wish> findByCoupleIdOrderByCreatedAtDesc(Long coupleId);

    java.util.Optional<Wish> findByIdAndCoupleId(Long id, Long coupleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wish w WHERE w.id = :id AND w.coupleId = :coupleId")
    java.util.Optional<Wish> findByIdAndCoupleIdLocked(@Param("id") Long id, @Param("coupleId") Long coupleId);

    /**
     * 查询该couple下 状态为PENDING_APPROVAL（待对方同意）的数量
     * 用于redeem/apply前超3个拦截(40806)
     */
    @Query("SELECT COUNT(w) FROM Wish w WHERE w.coupleId = :coupleId AND w.status = 'PENDING_APPROVAL'")
    long countPendingApproval(@Param("coupleId") Long coupleId);
}