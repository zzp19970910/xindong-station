package com.xindong.interactive.repository;

import com.xindong.interactive.entity.IcebreakTaskRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IcebreakTaskRecordRepository extends JpaRepository<IcebreakTaskRecord, Long> {

    Page<IcebreakTaskRecord> findByCoupleIdOrderByCreatedAtDesc(Long coupleId, Pageable pageable);
}