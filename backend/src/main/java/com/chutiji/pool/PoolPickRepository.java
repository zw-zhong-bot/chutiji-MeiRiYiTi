package com.chutiji.pool;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PoolPickRepository extends JpaRepository<PoolPickEntity, Long> {

    /** 该用户自 since 起拿过的池题 id（只回 id，够用且不回整行）。 */
    @Query("SELECT p.questionId FROM PoolPickEntity p WHERE p.userId = :userId AND p.createdAt >= :since")
    List<String> findRecentQuestionIds(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
