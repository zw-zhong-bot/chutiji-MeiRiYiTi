package com.chutiji.review;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewCardRepository extends JpaRepository<ReviewCardEntity, Long> {

    Optional<ReviewCardEntity> findByUserIdAndKnowledgeKey(Long userId, String knowledgeKey);

    Optional<ReviewCardEntity> findByIdAndUserId(Long id, Long userId);

    /** 到期卡：due_at <= now，早到期的排前面。 */
    List<ReviewCardEntity> findByUserIdAndDueAtLessThanEqualOrderByDueAtAsc(Long userId, LocalDateTime now);

    List<ReviewCardEntity> findByUserIdOrderByDueAtAsc(Long userId);
}
