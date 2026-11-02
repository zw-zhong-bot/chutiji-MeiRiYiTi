package com.chutiji.question;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<QuestionEntity, String> {

    /**
     * 批量删除已过期的实时生成题，最多删除 limit 条。
     *
     * <p>JPQL 不支持 LIMIT，故用原生 SQL。条件里带 source='GENERATED'：
     * 池题的 expires_at 为 NULL（设计文档 10.3 裁定 1），永远不会命中该条件。</p>
     *
     * @return 实际删除的行数
     */
    @Modifying
    @Query(value = "DELETE FROM t_question WHERE source = 'GENERATED' AND expires_at < :deadline LIMIT :limit",
            nativeQuery = true)
    int deleteExpiredGenerated(@Param("deadline") LocalDateTime deadline, @Param("limit") int limit);
}
