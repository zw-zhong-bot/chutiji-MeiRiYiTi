package com.chutiji.review;

import java.time.LocalDateTime;
import java.util.Map;

/** 复习接口的出入参。 */
public final class ReviewDtos {

    private ReviewDtos() {
    }

    /** 卡片视图：不含用户内部字段，难度配置原样回传便于前端展示。 */
    public record ReviewCardView(
            Long cardId,
            String moduleKey,
            Map<String, Object> difficulty,
            String knowledgeKey,
            double stability,
            double difficultyFsrs,
            int reps,
            int lapses,
            String state,
            LocalDateTime dueAt,
            LocalDateTime lastReviewAt,
            boolean due) {
    }

    /** 开始复习：返回新生成的题目（用卡片存的难度配置，模块内部不再随机）。 */
    public record ReviewStartView(
            Long cardId,
            String moduleKey,
            Map<String, Object> difficulty,
            String questionId,
            String prompt,
            Map<String, Object> payload,
            String type,
            int timeLimitSec,
            LocalDateTime expiresAt) {
    }
}
