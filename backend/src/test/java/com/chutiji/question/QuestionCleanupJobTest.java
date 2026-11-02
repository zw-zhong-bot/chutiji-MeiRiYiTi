package com.chutiji.question;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 清理任务的循环逻辑（不连库）：批次、终止条件与轮次上限。 */
class QuestionCleanupJobTest {

    /** 记录每次调用的参数与 deadline，并返回预设的批次结果。 */
    private static final class StubCleaner extends ExpiredQuestionCleaner {

        private final List<Integer> batchResults;
        final List<LocalDateTime> deadlines = new ArrayList<>();
        final List<Integer> limits = new ArrayList<>();

        StubCleaner(Integer... results) {
            super(null);
            this.batchResults = List.of(results);
        }

        @Override
        public int deleteBatch(LocalDateTime deadline, int limit) {
            deadlines.add(deadline);
            limits.add(limit);
            int index = deadlines.size() - 1;
            return index < batchResults.size() ? batchResults.get(index) : 0;
        }
    }

    @Test
    @DisplayName("某批返回不足 5000 条即停止，总数累加")
    void stopsWhenBatchIsShort() {
        StubCleaner cleaner = new StubCleaner(5000, 5000, 12);
        QuestionCleanupJob job = new QuestionCleanupJob(cleaner);

        int total = job.cleanupExpiredQuestions();

        assertEquals(10012, total);
        assertEquals(3, cleaner.limits.size());
        assertTrue(cleaner.limits.stream().allMatch(l -> l == QuestionCleanupJob.BATCH_SIZE));
    }

    @Test
    @DisplayName("每批都是满的时停在 20 批上限，不会无限循环")
    void stopsAtMaxBatches() {
        Integer[] full = new Integer[QuestionCleanupJob.MAX_BATCHES + 5];
        java.util.Arrays.fill(full, QuestionCleanupJob.BATCH_SIZE);
        StubCleaner cleaner = new StubCleaner(full);
        QuestionCleanupJob job = new QuestionCleanupJob(cleaner);

        int total = job.cleanupExpiredQuestions();

        assertEquals(QuestionCleanupJob.MAX_BATCHES * QuestionCleanupJob.BATCH_SIZE, total);
        assertEquals(QuestionCleanupJob.MAX_BATCHES, cleaner.limits.size());
    }

    @Test
    @DisplayName("首批就清空时只调用一次，deadline 取当前时间")
    void stopsAfterFirstEmptyBatch() {
        StubCleaner cleaner = new StubCleaner(0);
        QuestionCleanupJob job = new QuestionCleanupJob(cleaner);
        LocalDateTime before = LocalDateTime.now();

        int total = job.cleanupExpiredQuestions();

        assertEquals(0, total);
        assertEquals(1, cleaner.limits.size());
        assertTrue(!cleaner.deadlines.get(0).isBefore(before));
    }
}
