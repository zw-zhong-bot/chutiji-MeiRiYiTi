package com.chutiji.question;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 批量删除的 SQL 语义（连真实 MySQL，事务回滚不落库）：
 * 只删 source='GENERATED' 且已过期的题，不删未过期的、不删 POOL 题，且遵守 LIMIT。
 */
@SpringBootTest(properties = "app.question.cleanup-cron=-")
@Transactional
class ExpiredQuestionCleanupRepositoryTest {

    @Autowired
    private QuestionRepository questionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private QuestionEntity newQuestion(String id, String source, LocalDateTime expiresAt) {
        QuestionEntity entity = new QuestionEntity();
        entity.setId(id);
        entity.setModuleKey("arithmetic");
        entity.setQuestionType("FILL");
        entity.setDifficulty("{}");
        entity.setPrompt("测试题 " + id);
        entity.setPayload("{}");
        entity.setAnswer("{\"answer\":\"1\"}");
        entity.setSource(source);
        entity.setExpiresAt(expiresAt);
        return entity;
    }

    private boolean exists(String id) {
        entityManager.flush();
        entityManager.clear();
        return questionRepository.findById(id).isPresent();
    }

    @Test
    @DisplayName("只删过期的 GENERATED 题，未过期题与 POOL 题保留")
    void deletesOnlyExpiredGenerated() {
        LocalDateTime now = LocalDateTime.now();
        questionRepository.saveAll(List.of(
                newQuestion("itc_expired", "GENERATED", now.minusDays(1)),
                newQuestion("itc_future", "GENERATED", now.plusDays(1)),
                newQuestion("itc_pool", "POOL", null)));
        questionRepository.flush();

        int deleted = questionRepository.deleteExpiredGenerated(now, 5000);

        assertEquals(1, deleted);
        assertFalse(exists("itc_expired"), "已过期的 GENERATED 题应被删除");
        assertTrue(exists("itc_future"), "未过期的题不应被删除");
        assertTrue(exists("itc_pool"), "POOL 题（expires_at 为 NULL）不应被删除");
    }

    @Test
    @DisplayName("删除条数遵守 LIMIT，不会一次删光")
    void respectsLimit() {
        LocalDateTime now = LocalDateTime.now();
        questionRepository.saveAll(List.of(
                newQuestion("itc_lim_1", "GENERATED", now.minusHours(3)),
                newQuestion("itc_lim_2", "GENERATED", now.minusHours(2)),
                newQuestion("itc_lim_3", "GENERATED", now.minusHours(1))));
        questionRepository.flush();

        int first = questionRepository.deleteExpiredGenerated(now, 2);
        int second = questionRepository.deleteExpiredGenerated(now, 2);

        assertEquals(2, first);
        assertEquals(1, second);
    }
}
