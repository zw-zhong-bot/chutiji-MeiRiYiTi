-- 出题机 P2b：FSRS-5 复习引擎（设计文档 10.3 裁定 2.3）
-- 复习卡的粒度 = 模块 + 难度配置；knowledge_key 是这两者的稳定指纹

CREATE TABLE t_review_card (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    module_key      VARCHAR(32)  NOT NULL,
    difficulty      JSON         NOT NULL,
    knowledge_key   VARCHAR(128) NOT NULL,
    stability       DOUBLE       NOT NULL DEFAULT 0,
    difficulty_fsrs DOUBLE       NOT NULL DEFAULT 5,
    reps            INT          NOT NULL DEFAULT 0,
    lapses          INT          NOT NULL DEFAULT 0,
    state           VARCHAR(16)  NOT NULL,
    due_at          DATETIME     NOT NULL,
    last_review_at  DATETIME,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_card_user_knowledge (user_id, knowledge_key),
    INDEX idx_card_due (user_id, due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE t_review_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    card_id       BIGINT      NOT NULL,
    rating        TINYINT     NOT NULL,
    s_before      DOUBLE      NOT NULL,
    d_before      DOUBLE      NOT NULL,
    s_after       DOUBLE      NOT NULL,
    d_after       DOUBLE      NOT NULL,
    interval_days DOUBLE      NOT NULL,
    reviewed_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_log_card (card_id, reviewed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
