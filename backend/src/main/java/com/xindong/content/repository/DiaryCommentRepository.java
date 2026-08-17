package com.xindong.content.repository;

import com.xindong.content.entity.DiaryComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiaryCommentRepository extends JpaRepository<DiaryComment, Long> {

    List<DiaryComment> findByDiaryIdOrderByCreatedAtAsc(Long diaryId);

    Optional<DiaryComment> findByIdAndCoupleId(Long id, Long coupleId);
}