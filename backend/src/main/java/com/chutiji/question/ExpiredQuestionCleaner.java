package com.chutiji.question;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 过期题目清理的单批执行体。
 *
 * <p>事务边界只包住一次批量删除：调用方按批循环，每批一个事务，
 * 避免长事务锁表（设计文档 10.3 裁定 1）。</p>
 */
@Service
public class ExpiredQuestionCleaner {

    private final QuestionRepository questionRepository;

    public ExpiredQuestionCleaner(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    @Transactional
    public int deleteBatch(LocalDateTime deadline, int limit) {
        return questionRepository.deleteExpiredGenerated(deadline, limit);
    }
}
