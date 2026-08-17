package com.xindong.content.repository;

import com.xindong.content.entity.Diary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiaryRepository extends JpaRepository<Diary, Long> {

    Page<Diary> findByCoupleId(Long coupleId, Pageable pageable);

    Page<Diary> findByCoupleIdAndPartnerIdx(Long coupleId, Integer partnerIdx, Pageable pageable);

    java.util.Optional<Diary> findByIdAndCoupleId(Long id, Long coupleId);

    List<Diary> findByCoupleIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long coupleId, java.time.LocalDateTime start, java.time.LocalDateTime end);
}