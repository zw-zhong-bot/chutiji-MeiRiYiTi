-- 出题机 P6：每日一题 + 池题取用流水

CREATE TABLE t_daily_question (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    daily_date  DATE        NOT NULL,
    question_id VARCHAR(32) NOT NULL,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_daily_user_date (user_id, daily_date),
    INDEX idx_daily_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 池题取用流水：同一用户近期拿过的池题不再重复下发（任务书约束 6）。
-- 记的是「下发」而不是「作答」：题先下发后作答，所以这份流水天然覆盖「已做过」。
CREATE TABLE t_pool_pick (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    question_id VARCHAR(32) NOT NULL,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pick_user_time (user_id, created_at),
    INDEX idx_pick_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
