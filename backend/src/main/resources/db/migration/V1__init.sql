-- 出题机 Phase 0 初始化：用户、题目、作答记录
-- 表结构由 Flyway 独占管理，JPA 侧 ddl-auto=validate 只做校验

CREATE TABLE t_user (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(64)  NOT NULL,
    email         VARCHAR(128),
    role          VARCHAR(16)  NOT NULL DEFAULT 'USER',
    enabled       TINYINT      NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE t_question (
    id            VARCHAR(32)  NOT NULL PRIMARY KEY,
    module_key    VARCHAR(32)  NOT NULL,
    question_type VARCHAR(16)  NOT NULL,
    difficulty    JSON         NOT NULL,
    prompt        TEXT         NOT NULL,
    payload       JSON         NOT NULL,
    answer        JSON         NOT NULL,
    explanation   TEXT,
    source        VARCHAR(16)  NOT NULL,
    user_id       BIGINT,
    expires_at    DATETIME,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_question_module (module_key, source),
    INDEX idx_question_expire (expires_at),
    INDEX idx_question_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE t_answer_record (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT,
    question_id VARCHAR(32) NOT NULL,
    module_key  VARCHAR(32) NOT NULL,
    submitted   JSON        NOT NULL,
    correct     TINYINT     NOT NULL,
    duration_ms INT,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_record_user (user_id, created_at),
    INDEX idx_record_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
