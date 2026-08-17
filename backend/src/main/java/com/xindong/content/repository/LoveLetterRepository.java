package com.xindong.content.repository;

import com.xindong.content.entity.LoveLetter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoveLetterRepository extends JpaRepository<LoveLetter, Long> {

    Optional<LoveLetter> findByIdAndCoupleId(Long id, Long coupleId);

    /**
     * 情侣情书列表 按创建时间倒序
     */
    Page<LoveLetter> findByCoupleIdOrderByCreatedAtDesc(Long coupleId, Pageable pageable);
}