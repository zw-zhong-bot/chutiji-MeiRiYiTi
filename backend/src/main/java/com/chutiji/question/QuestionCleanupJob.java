package com.chutiji.question;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 过期题目清理任务：每天凌晨分批删除过期的实时生成题。
 *
 * <p>设计文档 10.3 裁定 1：GENERATED 题 24 小时过期，POOL 题 expires_at 为 NULL、永久有效。
 * 每批 {@value #BATCH_SIZE} 条，单轮最多 {@value #MAX_BATCHES} 批，防止异常情况下死循环。</p>
 */
@Component
public class QuestionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(QuestionCleanupJob.class);

    /** 每批删除条数。 */
    static final int BATCH_SIZE = 5000;

    /** 单轮最多批次数。 */
    static final int MAX_BATCHES = 20;

    private final ExpiredQuestionCleaner cleaner;

    public QuestionCleanupJob(ExpiredQuestionCleaner cleaner) {
        this.cleaner = cleaner;
    }

    /** 每天 03:30 触发；cron 可用 app.question.cleanup-cron 覆盖。 */
    @Scheduled(cron = "${app.question.cleanup-cron:0 30 3 * * *}")
    public void scheduledCleanup() {
        cleanupExpiredQuestions();
    }

    /**
     * 分批清理，直到某批返回行数小于 {@value #BATCH_SIZE} 或达到 {@value #MAX_BATCHES} 批为止。
     *
     * @return 本次共清理的条数
     */
    public int cleanupExpiredQuestions() {
        log.info("开始清理过期题目：source=GENERATED，每批最多 {} 条，单轮最多 {} 批", BATCH_SIZE, MAX_BATCHES);
        int total = 0;
        for (int round = 1; round <= MAX_BATCHES; round++) {
            int deleted = cleaner.deleteBatch(LocalDateTime.now(), BATCH_SIZE);
            total += deleted;
            if (deleted < BATCH_SIZE) {
                break;
            }
        }
        log.info("本次共清理 {} 条过期题目", total);
        return total;
    }
}
