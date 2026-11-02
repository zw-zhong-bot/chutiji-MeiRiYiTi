package com.chutiji.review;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chutiji.question.AnswerRecordEntity;

/**
 * 读取"该卡"的历史用时样本，用于把答对映射成 Hard/Good/Easy。
 *
 * <p>t_review_log 按设计文档 10.3 裁定 2.3 只存 S/D 快照，没有用时列，所以用时样本取自
 * t_answer_record；卡片的粒度是「模块 + 难度配置」，因此按 question_id 关联 t_question 后
 * 用难度 JSON 精确过滤（MySQL 的 JSON 相等比较与键序无关），拿到的就是同一档难度的历史样本。
 */
public interface ReviewDurationSampleRepository extends JpaRepository<AnswerRecordEntity, Long> {

    @Query(value = "SELECT r.duration_ms FROM t_answer_record r "
            + "JOIN t_question q ON q.id = r.question_id "
            + "WHERE r.user_id = :userId AND r.correct = 1 AND r.duration_ms IS NOT NULL "
            + "AND q.module_key = :moduleKey "
            + "AND q.difficulty = CAST(:difficulty AS JSON)", nativeQuery = true)
    List<Integer> findCorrectDurations(@Param("userId") Long userId,
                                       @Param("moduleKey") String moduleKey,
                                       @Param("difficulty") String difficultyJson);
}
