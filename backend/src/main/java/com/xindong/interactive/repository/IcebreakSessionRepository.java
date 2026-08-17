package com.xindong.interactive.repository;

import com.xindong.interactive.entity.IcebreakSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IcebreakSessionRepository extends JpaRepository<IcebreakSession, Long> {

    Optional<IcebreakSession> findByCoupleIdAndDateStr(Long coupleId, String dateStr);
}