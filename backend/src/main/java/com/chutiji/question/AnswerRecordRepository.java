package com.chutiji.question;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerRecordRepository extends JpaRepository<AnswerRecordEntity, Long> {

    /** 只取当前用户自己的作答记录；排序由调用方传入的 Pageable 决定。 */
    Page<AnswerRecordEntity> findByUserId(Long userId, Pageable pageable);
}
