package com.chutiji.question;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 取某个模块的全部池题，供取题时按难度过滤。
     *
     * <p>难度是 JSON 列，「按键排序后逐字段比对」在 SQL 里表达不出来（MySQL 的
     * JSON_CONTAINS 比的是文档结构、不是规格化后的等价），所以候选集按模块拉回来，
     * 等价判断在 Java 里做一次规范文本比较。单模块池子的量级是几百到几千行，可接受。</p>
     */
    @Query("SELECT q FROM QuestionEntity q WHERE q.moduleKey = :moduleKey AND q.source = 'POOL'")
    List<QuestionEntity> findPoolByModule(@Param("moduleKey") String moduleKey);
}
