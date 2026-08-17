package com.xindong.incentive.repository;

import com.xindong.incentive.entity.WishOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WishOrderRepository extends JpaRepository<WishOrder, Long> {

    boolean existsByWishIdAndCoupleId(Long wishId, Long coupleId);
}