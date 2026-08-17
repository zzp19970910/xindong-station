package com.xindong.interactive.repository;

import com.xindong.interactive.entity.TacitGame;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TacitGameRepository extends JpaRepository<TacitGame, Long> {

    Page<TacitGame> findByCoupleIdOrderByCreatedAtDesc(Long coupleId, Pageable pageable);
}