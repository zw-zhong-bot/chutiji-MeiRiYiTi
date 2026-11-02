package com.chutiji.review;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewLogRepository extends JpaRepository<ReviewLogEntity, Long> {

    List<ReviewLogEntity> findByCardIdOrderByReviewedAtAsc(Long cardId);
}
