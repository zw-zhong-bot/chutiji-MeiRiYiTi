package com.chutiji.review;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewLogRepository extends JpaRepository<ReviewLogEntity, Long> {

    List<ReviewLogEntity> findByCardIdOrderByReviewedAtAsc(Long cardId);

    /**
     * 某个用户从 {@code from} 起按自然日聚合的复习量。t_review_log 没有 user_id 列，
     * 因此按 card_id 关联 t_review_card 过滤用户（设计文档 10.3 裁定 2.3 的表结构）。
     *
     * <p>返回列：日期、评分条数、其中 rating &gt; 1 的条数（rating=1 是 Again 即答错）。
     */
    @Query(value = "SELECT DATE(l.reviewed_at) AS day, COUNT(*) AS reviews, "
            + "SUM(CASE WHEN l.rating > 1 THEN 1 ELSE 0 END) AS correct "
            + "FROM t_review_log l JOIN t_review_card c ON c.id = l.card_id "
            + "WHERE c.user_id = :userId AND l.reviewed_at >= :from "
            + "GROUP BY DATE(l.reviewed_at)", nativeQuery = true)
    List<Object[]> countByDaySince(@Param("userId") Long userId, @Param("from") LocalDateTime from);
}
